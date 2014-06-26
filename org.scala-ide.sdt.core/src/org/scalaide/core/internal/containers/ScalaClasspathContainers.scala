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
import org.scalaide.util.internal.SettingConverterUtil

abstract class ScalaClasspathContainerInitializer(desc: String) extends ClasspathContainerInitializer with HasLogger {
  def entries: Array[IClasspathEntry]

  override def initialize(containerPath: IPath, project: IJavaProject) = {
    val iProject = project.getProject()

    val storage = new PropertyStore(new ProjectScope(iProject), ScalaPlugin.plugin.pluginId)
    val setter = new ClasspathContainerSetter(project)
    val usesProjectSettings = storage.getBoolean(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE)

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

abstract class ScalaClasspathContainerPage(containerPath: IPath, name: String, override val itemTitle: String, desc: String) extends NewElementWizardPage(name)
  with ScalaClasspathContainerHandler
  with IClasspathContainerPage
  with IClasspathContainerPageExtension
  with ScalaInstallationUIProviders {

  private var chosenScalaInstallation: ScalaInstallation = null
  private var existingEntries: Array[IClasspathEntry] = null
  protected var project: IJavaProject = null
  private var versionString: String = " none "

  setTitle(itemTitle)
  setDescription(desc)
  setImageDescriptor(JavaPluginImages.DESC_WIZBAN_ADD_LIBRARY)

  override def finish() = true

  override def getSelection(): IClasspathEntry = getAndUpdateScalaClasspathContainerEntry(containerPath, desc, versionString, project, chosenScalaInstallation, existingEntries)

  override def initialize(javaProject: IJavaProject, currentEntries: Array[IClasspathEntry]) = {
    project = javaProject
    existingEntries = currentEntries
  }

  override def setSelection(containerEntry: IClasspathEntry) {}

  override def createControl(parent: Composite) = {
    val composite = new Composite(parent, SWT.NONE)

    composite.setLayout(new GridLayout(2, false))

    val list = new ListViewer(composite)
    list.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
    list.setContentProvider(new ContentProvider())
    list.setLabelProvider(new LabelProvider)
    list.setInput(ScalaInstallation.availableInstallations)

    list.addSelectionChangedListener(new ISelectionChangedListener() {
      def selectionChanged(event: SelectionChangedEvent) {
        try {
          val sel = event.getSelection().asInstanceOf[IStructuredSelection]
          val si = sel.getFirstElement().asInstanceOf[ScalaInstallation]
          chosenScalaInstallation = si
          versionString = si.version.unparse
          setPageComplete(true)
        } catch {
          case e: Exception =>
            logger.error("Exception during selection of a scala bundle", e)
            chosenScalaInstallation = null
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
    override def containerUpdater(containerPath: IPath, container: IClasspathContainer) = (new ScalaCompilerClasspathContainerInitializer()).requestClasspathContainerUpdate(containerPath, project, container)
}

class ScalaLibraryClasspathContainerPage extends
  ScalaClasspathContainerPage(new Path(ScalaPlugin.plugin.scalaLibId),
    "ScalaLibraryContainerPage",
    "Scala Library container",
    "Scala library container") {
    override def classpathEntriesOfScalaInstallation(si: ScalaInstallation): Array[IClasspathEntry] = (si.library +: si.extraJars).map(_.libraryEntries()).toArray
    override def containerUpdater(containerPath: IPath, container: IClasspathContainer) = (new ScalaLibraryClasspathContainerInitializer()).requestClasspathContainerUpdate(containerPath, project, container)
}
