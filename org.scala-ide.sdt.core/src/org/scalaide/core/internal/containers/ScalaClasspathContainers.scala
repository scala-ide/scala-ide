package org.scalaide.core.internal.containers

import java.util.Properties
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
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.LabelProvider
import org.eclipse.jface.viewers.ListViewer
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.jface.viewers.Viewer
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Link
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.jdt.util.ClasspathContainerSetter
import org.scalaide.core.internal.jdt.util.ScalaClasspathContainerHandler
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.core.internal.project.ScalaInstallation.platformInstallation.compiler
import org.scalaide.core.internal.project.ScalaInstallation.platformInstallation.library
import org.scalaide.core.internal.project.ScalaInstallationChoice
import org.scalaide.core.internal.project.ScalaModule
import org.scalaide.core.internal.project.ScalaProject
import org.scalaide.logging.HasLogger
import org.scalaide.ui.internal.project.ScalaInstallationChoiceUIProviders
import org.scalaide.util.internal.CompilerUtils.ShortScalaVersion
import org.scalaide.util.internal.CompilerUtils.shortString
import org.scalaide.ui.internal.preferences.CompilerSettings
import org.scalaide.util.internal.SettingConverterUtil
import org.eclipse.ui.dialogs.PreferencesUtil

abstract class ScalaClasspathContainerInitializer(desc: String) extends ClasspathContainerInitializer with HasLogger {
  def entries: Array[IClasspathEntry]

  override def initialize(containerPath: IPath, project: IJavaProject) = {
    val iProject = project.getProject()

    val setter = new ClasspathContainerSetter(project)
    val proj =     ScalaPlugin.plugin.asScalaProject(iProject)
    val install = proj map (_.getDesiredInstallation())

    if (proj.isDefined) setter.updateBundleFromScalaInstallation(containerPath, install.get)
    else {
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

  override def entries = (library +: ScalaInstallation.platformInstallation.extraJars).map {_.libraryEntries()}.to[Array]
}

class ScalaCompilerClasspathContainerInitializer extends ScalaClasspathContainerInitializer("Scala compiler container") {
  val plugin = ScalaPlugin.plugin
  import ScalaInstallation.platformInstallation._

  override def entries = Array(compiler.libraryEntries())
}

abstract class ScalaClasspathContainerPage(containerPath: IPath, name: String, override val itemTitle: String, desc: String) extends NewElementWizardPage(name)
  with ScalaClasspathContainerHandler
  with IClasspathContainerPage
  with IClasspathContainerPageExtension
  with ScalaInstallationChoiceUIProviders {
  import org.scalaide.util.internal.eclipse.SWTUtils._

  private var choiceOfScalaInstallation: ScalaInstallationChoice = null
  protected var scalaProject: Option[ScalaProject] = None
  private var createControlDelegate: Composite => Unit = (parent:Composite) => {}
  private var finishDelegate: () => Boolean = () => {true}

  setTitle(itemTitle)
  setDescription(desc)
  setImageDescriptor(JavaPluginImages.DESC_WIZBAN_ADD_LIBRARY)

  override def finish() = finishDelegate()

  override def getSelection(): IClasspathEntry = { JavaCore.newContainerEntry(containerPath) }

  override def initialize(javaProject: IJavaProject, currentEntries: Array[IClasspathEntry]) = {
    scalaProject = ScalaPlugin.plugin.asScalaProject(javaProject.getProject())
    val rcp = javaProject.getRawClasspath()
    if (hasCustomContainer(rcp, new Path(ScalaPlugin.plugin.scalaLibId), IClasspathEntry.CPE_CONTAINER) && hasCustomContainer(rcp, new Path(ScalaPlugin.plugin.scalaCompilerId), IClasspathEntry.CPE_CONTAINER)) {
      createControlDelegate = { (parent: Composite) =>
        setTitle("Unhandled edition case")
        setDescription("You have both scala compiler and library classpath containers on classpath. \n Editing only one of them from this menu can lead to inconsistencies and is not supported.")

        val composite = new Composite(parent, SWT.NONE)
        composite.setLayout(new GridLayout())
        setControl(composite)

        val link = new Link(composite, SWT.BORDER);
        link.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        link.setText("The best way to handle these two containers' content is through compiler settings. Go to <a>project's compiler settings</a> to set the Scala installation.")
        link.addSelectionListener(() =>
          PreferencesUtil.createPropertyDialogOn(getShell(), javaProject.getProject(),
            CompilerSettings.PAGE_ID, Array(CompilerSettings.PAGE_ID), null).open()
        )
        setControl(composite)
      }
    } else {
      createControlDelegate = { (parent: Composite) =>
        val composite = new Composite(parent, SWT.NONE)

        composite.setLayout(new GridLayout(2, false))

        val list = new ListViewer(composite)
        list.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
        list.setContentProvider(new ContentProvider())
        list.setLabelProvider(new LabelProvider)
        val previousVersionChoice = PartialFunction.condOpt(ScalaInstallation.platformInstallation.version) { case ShortScalaVersion(major, minor) => ScalaInstallationChoice(ScalaVersion(f"$major%d.${minor - 1}%d")) }
        def previousVersionPrepender(l: List[ScalaInstallationChoice]) = previousVersionChoice.fold(l)(s => s :: l)
        list.setInput(ScalaInstallationChoice(ScalaPlugin.plugin.scalaVer) :: previousVersionPrepender(ScalaInstallation.availableInstallations.map(si => ScalaInstallationChoice(si))))
        val initialSelection = scalaProject map (_.getDesiredInstallationChoice())
        initialSelection foreach { choice => list.setSelection(new StructuredSelection(choice)) }

        list.addSelectionChangedListener({ (event: SelectionChangedEvent) =>
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
        })

        setControl(composite)
      }
      finishDelegate = { () =>
        scalaProject foreach { pr =>
          Option(choiceOfScalaInstallation) foreach { sc =>
            pr.projectSpecificStorage.setValue(SettingConverterUtil.SCALA_DESIRED_INSTALLATION, sc.toString())
          }
        }
        scalaProject.isDefined
      }
    }

  }

  override def setSelection(containerEntry: IClasspathEntry): Unit = {}

  override def createControl(parent: Composite): Unit = createControlDelegate(parent)

}

class ScalaCompilerClasspathContainerPage extends
  ScalaClasspathContainerPage(
    new Path(ScalaPlugin.plugin.scalaCompilerId),
    "ScalaCompilerContainerPage",
    "Scala Compiler container",
    "Scala compiler container") {}

class ScalaLibraryClasspathContainerPage extends
  ScalaClasspathContainerPage(new Path(ScalaPlugin.plugin.scalaLibId),
    "ScalaLibraryContainerPage",
    "Scala Library container",
    "Scala library container") {}
