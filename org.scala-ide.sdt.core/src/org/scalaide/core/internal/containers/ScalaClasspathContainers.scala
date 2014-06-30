package org.scalaide.core.internal.containers

import java.util.Properties
import java.util.zip.ZipFile
import scala.tools.nsc.settings.ScalaVersion
import org.eclipse.core.resources.ProjectScope
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.ClasspathContainerInitializer
import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.jdt.ui.wizards.IClasspathContainerPage
import org.eclipse.jdt.ui.wizards.IClasspathContainerPageExtension
import org.eclipse.jdt.ui.wizards.NewElementWizardPage
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.ListViewer
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.jdt.util.ClasspathContainerSetter
import org.scalaide.core.internal.jdt.util.ScalaClasspathContainerHandler
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.core.internal.project.ScalaInstallation.platformInstallation.compiler
import org.scalaide.core.internal.project.ScalaInstallation.platformInstallation.library
import org.scalaide.core.internal.project.ScalaModule
import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.preferences.PropertyStore
import org.scalaide.ui.internal.project.ScalaInstallationUIProviders
import org.eclipse.jface.viewers.LabelProvider
import org.scalaide.util.internal.SettingConverterUtil
import org.scalaide.ui.internal.preferences.PropertyStore
import org.eclipse.core.resources.ProjectScope
import scala.tools.nsc.settings.ScalaVersion
import org.scalaide.core.internal.jdt.util.ClasspathContainerSetter
import org.scalaide.core.internal.jdt.util.ScalaClasspathContainerHandler
import java.util.zip.ZipFile
import java.util.Properties
import org.scalaide.core.internal.project.LabeledScalaInstallation
import org.scalaide.core.internal.project.ScalaInstallationChoice
import org.scalaide.util.internal.CompilerUtils.ShortScalaVersion
import org.scalaide.util.internal.CompilerUtils.shortString
import org.eclipse.jface.viewers.IStructuredContentProvider
import org.eclipse.jface.viewers.Viewer

abstract class ScalaClasspathContainerInitializer(desc: String) extends ClasspathContainerInitializer with HasLogger {
  def entries: Array[IClasspathEntry]

  override def initialize(containerPath: IPath, project: IJavaProject) = {
    val iProject = project.getProject()

    val storage = new PropertyStore(new ProjectScope(iProject), ScalaPlugin.plugin.pluginId)
    val setter = new ClasspathContainerSetter(project)
    val usesProjectSettings = storage.getBoolean(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE)

    if (usesProjectSettings && storage.contains(SettingConverterUtil.SCALA_DESIRED_INSTALLATION) && !storage.isDefault(SettingConverterUtil.SCALA_DESIRED_INSTALLATION)) {
      val desiredInstallChoice = ScalaPlugin.plugin.asScalaProject(iProject) flatMap (_.parseScalaInstallation(storage.getString(SettingConverterUtil.SCALA_DESIRED_INSTALLATION)))
      desiredInstallChoice foreach {sc => ScalaInstallation.resolve(sc) foreach (setter.updateBundleFromScalaInstallation(containerPath, _))}
    }
    else
    if (usesProjectSettings && !storage.isDefault(SettingConverterUtil.SCALA_DESIRED_SOURCELEVEL)) {
      setter.updateBundleFromSourceLevel(containerPath, ScalaVersion(storage.getString(SettingConverterUtil.SCALA_DESIRED_SOURCELEVEL)))
    } else {
      logger.info(s"Initializing classpath container $desc: ${entries foreach (_.getPath())}")

      JavaCore.setClasspathContainer(containerPath, Array(project), Array(new IClasspathContainer {
        override def getPath = containerPath
        override def getClasspathEntries = entries
        override def getDescription = desc + " [" + scala.util.Properties.scalaPropOrElse("version.number", "none") + "]"
        override def getKind = IClasspathContainer.K_SYSTEM
      }), null)
    }
  }
}

class ScalaLibraryClasspathContainerInitializer extends ScalaClasspathContainerInitializer("Scala library container") {
  val plugin = ScalaPlugin.plugin
  import plugin._

  override def entries = (library +: ScalaInstallation.platformInstallation.extraJars).map {_.libraryEntries()}.to[Array]
}

class ScalaCompilerClasspathContainerInitializer extends ScalaClasspathContainerInitializer("Scala compiler container") {
  val plugin = ScalaPlugin.plugin
  import plugin._
  import ScalaInstallation.platformInstallation._

  override def entries = Array(compiler.libraryEntries())
}

abstract class ScalaClasspathContainerPage(containerPath: IPath, name: String, itemTitle: String, desc: String) extends NewElementWizardPage(name)
  with ScalaClasspathContainerHandler
  with IClasspathContainerPage
  with IClasspathContainerPageExtension {

  class ContentProvider extends IStructuredContentProvider {
    override def dispose(): Unit = {}

    override def inputChanged(viewer: Viewer, oldInput: Any, newInput: Any): Unit = {}

    override def getElements(input: Any): Array[Object] = {
      input match {
        case l: List[ScalaInstallationChoice] =>
          l.toArray
      }
    }
  }

  class LabelProvider extends org.eclipse.jface.viewers.LabelProvider {

    override def getText(element: Any): String = element match {
      case ch: ScalaInstallationChoice => ch.marker match {
        case Left(scalaVersion) => s"Dynamic $itemTitle : ${shortString(scalaVersion)}"
        case Right(hashcode) => s"Fixed $itemTitle : ${ScalaInstallation.resolve(ch) map (_.version.unparse) getOrElse " none "}"
      }
    }
  }

  private var choiceOfScalaInstallation: ScalaInstallationChoice = null
  protected var project: IJavaProject = null

  setTitle(itemTitle)
  setDescription(desc)
  setImageDescriptor(JavaPluginImages.DESC_WIZBAN_ADD_LIBRARY)

  override def finish() = {
    val proj = ScalaPlugin.plugin.asScalaProject(project.getProject())
    proj foreach {pr =>
      pr.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, choiceOfScalaInstallation.toString())
    }
    proj.isDefined
  }

  override def getSelection(): IClasspathEntry = { JavaCore.newContainerEntry(containerPath) }

  override def initialize(javaProject: IJavaProject, currentEntries: Array[IClasspathEntry]) = {
    project = javaProject
  }

  override def setSelection(containerEntry: IClasspathEntry) = {}

  override def createControl(parent: Composite) = {
    val composite = new Composite(parent, SWT.NONE)

    composite.setLayout(new GridLayout(2, false))

    val list = new ListViewer(composite)
    list.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
    list.setContentProvider(new ContentProvider())
    list.setLabelProvider(new LabelProvider)
    val previousVersionChoice = PartialFunction.condOpt(ScalaInstallation.platformInstallation.version) {case ShortScalaVersion(major, minor) => ScalaInstallationChoice(Left(ScalaVersion(f"$major%d.${minor-1}%d")))}
    def previousVersionPrepender(l:List[ScalaInstallationChoice]) = previousVersionChoice.fold(l)(s => s :: l)

    list.setInput( previousVersionPrepender(ScalaInstallationChoice(Left(ScalaInstallation.platformInstallation.version)) :: ScalaInstallation.availableInstallations.map(si => ScalaInstallationChoice(Right(si.getHashString().hashCode())))) )

    list.addSelectionChangedListener(new ISelectionChangedListener() {
      def selectionChanged(event: SelectionChangedEvent) {
        try {
          val sel = event.getSelection().asInstanceOf[IStructuredSelection]
          val sc = sel.getFirstElement().asInstanceOf[ScalaInstallationChoice]
          choiceOfScalaInstallation = sc
          setPageComplete(true)
        } catch {
          case e: Exception =>
            logger.error("Exception during selection of a scala bundle", e)
            choiceOfScalaInstallation = null
            setPageComplete(false)
        }
      }
    })

    setControl(composite)
  }

}

class ScalaCompilerClasspathContainerPage extends
  ScalaClasspathContainerPage(
    new Path(ScalaPlugin.plugin.scalaCompilerId),
    "ScalaCompilerContainerPage",
    "Scala Compiler container",
    "Scala compiler container") {
    override def classpathEntriesOfScalaInstallation(si: ScalaInstallation): Array[IClasspathEntry] = Array(si.compiler.libraryEntries())
}

class ScalaLibraryClasspathContainerPage extends
  ScalaClasspathContainerPage(new Path(ScalaPlugin.plugin.scalaLibId),
    "ScalaLibraryContainerPage",
    "Scala Library container",
    "Scala library container") {
    override def classpathEntriesOfScalaInstallation(si: ScalaInstallation): Array[IClasspathEntry] = (si.library +: si.extraJars).map(_.libraryEntries()).toArray
}
