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

trait ScalaClasspathContainerHandler extends ClasspathContainerSerializer with HasLogger {

  def classpathEntriesOfScalaInstallation(si: ScalaInstallation): Array[IClasspathEntry]

  def containerUpdater(containerPath: IPath, container: IClasspathContainer)

  private def hasCustomContainer(existingEntries: Array[IClasspathEntry], cp: IPath): Boolean = {
   existingEntries.exists(e => e.getEntryKind() == IClasspathContainer.K_SYSTEM && e.getPath().equals(cp))
  }

  def getAndUpdateScalaClasspathContainerEntry(id: String, desc: String, versionString: String, project: IJavaProject, si:ScalaInstallation, existingEntries: Array[IClasspathEntry]): IClasspathEntry = {
    val containerPath: IPath = if (project != null) new Path(id).append(project.getPath()) else new Path(id)

    val customContainer : IClasspathContainer = new IClasspathContainer() {
      override def getClasspathEntries() = classpathEntriesOfScalaInstallation(si)
      override def getDescription(): String = desc + s" [ $versionString ]"
      override def getKind(): Int = IClasspathContainer.K_SYSTEM
      override def getPath(): IPath = containerPath
    }

   if (!hasCustomContainer(existingEntries, containerPath)) {
      logger.debug(s"Did not find a container for $id on classpath when asked to update to $versionString — adding Container")
      JavaCore.setClasspathContainer(containerPath, Array(project),Array(customContainer), null)
   }
   else {
     logger.debug(s"Found container for $id on classpath when asked to update to $versionString — updating existing semantics")
     containerUpdater(containerPath, customContainer)
   }
   saveContainerState(project.getProject(), customContainer)
   // JavaCore.setClasspathContainer(containerPath, Array(project),Array(customContainer), null)
   if (!hasCustomContainer(existingEntries, containerPath)) JavaCore.newContainerEntry(containerPath) else null
  }
}

trait ClasspathContainerSerializer extends HasLogger {
  import org.scalaide.core.internal.jdt.util.ClasspathContainerSaveHelper._

  protected def libraryEntries(lib: ScalaModule): IClasspathEntry = {
    if (lib.sourceJar.isEmpty) logger.debug(s"No source attachements for ${lib.classJar.lastSegment()}")

    JavaCore.newLibraryEntry(lib.classJar, lib.sourceJar.orNull, null)
  }

  def getContainerStateFile(project:IProject) = {
    new File(ScalaPlugin.plugin.getStateLocation().toFile(), project.getName() + ".container")
  }

  def saveContainerState(project: IProject, container: IClasspathContainer): Unit = {
    val containerStateFile = getContainerStateFile(project)
    val containerStateFilePath = containerStateFile.getPath()
    logger.debug(s"Trying to write classpath container state to $containerStateFilePath")
    var is: FileOutputStream = null
    try {
      is = new FileOutputStream(containerStateFile)
      writeContainer(container, is)
    } catch {
      case ex: IOException =>
        logger.error("Can't save classpath container state for " + project.getName(), ex)
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch {
          case ex: IOException => logger.error("Can't close output stream for " + containerStateFile.getAbsolutePath(), ex)
        } finally logger.debug(s"Successfully wrote classpath container state to $containerStateFilePath")
      }

    }
  }

  def getSavedContainer(project: IProject): Option[IClasspathContainer] = {
    val containerStateFile = getContainerStateFile(project)
    val containerStateFilePath = containerStateFile.getPath()
    logger.debug(s"Trying to read classpath container state from $containerStateFilePath")
    if (!containerStateFile.exists()) None
    else {
      var is: FileInputStream = null
      try {
        is = new FileInputStream(containerStateFile)
        Some(readContainer(is))
      } catch {
        case ex: IOException =>
          throw new CoreException(new Status(IStatus.ERROR, ScalaPlugin.plugin.pluginId, -1,
            "Can't read classpath container state for " + project.getName(), ex))
        case ex: ClassNotFoundException =>
          throw new CoreException(new Status(IStatus.ERROR, ScalaPlugin.plugin.pluginId, -1,
            "Can't read classpath container state for " + project.getName(), ex))
      } finally {
        if (is != null) {
          try {
            is.close();
          } catch {
            case ex: IOException => logger.error("Can't close output stream for " + containerStateFile.getAbsolutePath(), ex)
          } finally logger.debug(s"Successfully read classpath container state from $containerStateFilePath")
        }
      }
    }
  }

}

abstract class ScalaClasspathContainerInitializer(desc: String) extends ClasspathContainerInitializer with ClasspathContainerSerializer with HasLogger {
  def entries: Array[IClasspathEntry]

  override def canUpdateClasspathContainer(containerPath: IPath, project: IJavaProject)= true

  override def initialize(containerPath: IPath, project: IJavaProject) = {
    val savedContainer = getSavedContainer(project.getProject())
    if (savedContainer.isDefined) JavaCore.setClasspathContainer(containerPath, Array(project), Array(savedContainer.get), new NullProgressMonitor())
    else {
      logger.info(s"Initializing classpath container $desc: ${library.classJar}")
      logger.info(s"Initializing classpath container $desc with sources: ${library.sourceJar}")

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

  override def entries = (library +: ScalaInstallation.platformInstallation.extraJars).map {libraryEntries}.to[Array]
}

class ScalaCompilerClasspathContainerInitializer extends ScalaClasspathContainerInitializer("Scala compiler container") {
  val plugin = ScalaPlugin.plugin
  import plugin._
  import ScalaInstallation.platformInstallation._

  override def entries = Array(libraryEntries(compiler))
}

abstract class ScalaClasspathContainerPage(id: String, name: String, override val title: String, desc: String) extends NewElementWizardPage(name)
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

  override def getSelection(): IClasspathEntry = getAndUpdateScalaClasspathContainerEntry(id, desc, versionString, project, chosenScalaInstallation, existingEntries)

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
    ScalaPlugin.plugin.scalaCompilerId,
    "ScalaCompilerContainerPage",
    "Scala Compiler Container",
    "Scala compiler container") {
    override def classpathEntriesOfScalaInstallation(si: ScalaInstallation): Array[IClasspathEntry] = Array(libraryEntries(si.compiler))
    override def containerUpdater(containerPath: IPath, container: IClasspathContainer) = (new ScalaCompilerClasspathContainerInitializer()).requestClasspathContainerUpdate(containerPath, project, container)
}

class ScalaLibraryClasspathContainerPage extends
  ScalaClasspathContainerPage(ScalaPlugin.plugin.scalaLibId,
    "ScalaLibraryContainerPage",
    "Scala Library Container",
    "Scala library container") {
    override def classpathEntriesOfScalaInstallation(si: ScalaInstallation): Array[IClasspathEntry] = (si.library +: si.extraJars).map(libraryEntries).toArray
    override def containerUpdater(containerPath: IPath, container: IClasspathContainer) = (new ScalaLibraryClasspathContainerInitializer()).requestClasspathContainerUpdate(containerPath, project, container)
}
