package org.scalaide.core.internal.containers

import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.ClasspathContainerInitializer
import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.jdt.ui.wizards.IClasspathContainerPage
import org.eclipse.jdt.ui.wizards.NewElementWizardPage
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.core.internal.project.ScalaInstallation.platformInstallation._
import org.scalaide.logging.HasLogger
import org.scalaide.core.internal.project.ScalaModule
import org.eclipse.jface.viewers.IStructuredContentProvider
import org.scalaide.core.internal.project.BundledScalaInstallation
import org.eclipse.jface.viewers.Viewer
import org.scalaide.core.internal.project.MultiBundleScalaInstallation
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.jface.viewers.ListViewer
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Button
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jdt.ui.wizards.IClasspathContainerPageExtension
import java.io.IOException
import java.io.FileOutputStream
import org.eclipse.core.resources.IProject
import java.io.File
import org.scalaide.core.internal.jdt.util.ClasspathContainerSaveHelper
import org.eclipse.core.runtime.IStatus
import java.io.FileInputStream
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.NullProgressMonitor
import org.scalaide.ui.internal.project.ScalaInstallationUIProviders
import org.eclipse.jface.viewers.LabelProvider
import org.scalaide.util.internal.SettingConverterUtil
import org.scalaide.ui.internal.preferences.PropertyStore
import org.eclipse.core.resources.ProjectScope
import scala.tools.nsc.settings.ScalaVersion
import org.scalaide.core.internal.jdt.util.ClasspathContainerSetter
import org.scalaide.core.internal.jdt.util.ScalaClasspathContainerHandler
import org.scalaide.core.internal.jdt.util.ClasspathContainerSerializer

abstract class ScalaClasspathContainerInitializer(desc: String) extends ClasspathContainerInitializer with ClasspathContainerSerializer with HasLogger {
  def entries: Array[IClasspathEntry]

  override def canUpdateClasspathContainer(containerPath: IPath, project: IJavaProject)= true

  override def initialize(containerPath: IPath, project: IJavaProject) = {
    val iProject = project.getProject()
    val savedContainer = getSavedContainerForPath(iProject, containerPath)
    if (savedContainer.isDefined) JavaCore.setClasspathContainer(containerPath, Array(project), Array(savedContainer.get), new NullProgressMonitor())
    else {
      val storage = new PropertyStore(new ProjectScope(iProject), ScalaPlugin.plugin.pluginId)
      val usesProjectSettings = storage.getBoolean(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE)
      if (usesProjectSettings && storage.contains(SettingConverterUtil.SCALA_DESIRED_SOURCELEVEL) && !storage.isDefault(SettingConverterUtil.SCALA_DESIRED_SOURCELEVEL))
        new ClasspathContainerSetter(project).updateBundleFromSourceLevel(containerPath, ScalaVersion(storage.getString(SettingConverterUtil.SCALA_DESIRED_SOURCELEVEL)))
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

}

class ScalaLibraryClasspathContainerInitializer extends ScalaClasspathContainerInitializer("Scala library container") {
  val plugin = ScalaPlugin.plugin
  import plugin._

  override def entries = (library +: ScalaInstallation.platformInstallation.extraJars).map {libraryEntries}.to[Array]
}

class ScalaCompilerClasspathContainerInitializer extends ScalaClasspathContainerInitializer("Scala compiler container") {
  val plugin = ScalaPlugin.plugin
  import plugin._
  import ScalaInstallation.platformInstallation._

  override def entries = Array(libraryEntries(compiler))
}

abstract class ScalaClasspathContainerPage(containerPath: IPath, name: String, override val title: String, desc: String) extends NewElementWizardPage(name)
  with ScalaClasspathContainerHandler
  with IClasspathContainerPage
  with IClasspathContainerPageExtension
  with ScalaInstallationUIProviders {

  private var chosenScalaInstallation: ScalaInstallation = null
  private var existingEntries: Array[IClasspathEntry] = null
  protected var project: IJavaProject = null
  private var versionString: String = " none "

  setTitle(title)
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
    override def classpathEntriesOfScalaInstallation(si: ScalaInstallation): Array[IClasspathEntry] = Array(libraryEntries(si.compiler))
    override def containerUpdater(containerPath: IPath, container: IClasspathContainer) = (new ScalaCompilerClasspathContainerInitializer()).requestClasspathContainerUpdate(containerPath, project, container)
}

class ScalaLibraryClasspathContainerPage extends
  ScalaClasspathContainerPage(new Path(ScalaPlugin.plugin.scalaLibId),
    "ScalaLibraryContainerPage",
    "Scala Library container",
    "Scala library container") {
    override def classpathEntriesOfScalaInstallation(si: ScalaInstallation): Array[IClasspathEntry] = (si.library +: si.extraJars).map(libraryEntries).toArray
    override def containerUpdater(containerPath: IPath, container: IClasspathContainer) = (new ScalaLibraryClasspathContainerInitializer()).requestClasspathContainerUpdate(containerPath, project, container)
}
