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

abstract class ScalaClasspathContainerInitializer(desc: String) extends ClasspathContainerInitializer with HasLogger {
  def entries: Array[IClasspathEntry]

  override def initialize(containerPath: IPath, project: IJavaProject) = {
    logger.info(s"Initializing classpath container $desc: ${library.classJar}")
    logger.info(s"Initializing classpath container $desc with sources: ${library.sourceJar}")

    JavaCore.setClasspathContainer(containerPath, Array(project), Array(new IClasspathContainer {
      override def getPath = containerPath
      override def getClasspathEntries = entries
      override def getDescription = desc + " [" + scala.util.Properties.scalaPropOrElse("version.number", "none") + "]"
      override def getKind = IClasspathContainer.K_SYSTEM
    }), null)
  }

  protected def libraryEntries(lib: ScalaModule): IClasspathEntry = {
    if (lib.sourceJar.isEmpty) logger.debug(s"No source attachements for ${lib.classJar.lastSegment()}")

    JavaCore.newLibraryEntry(lib.classJar, lib.sourceJar.orNull, null)
  }
}

class ScalaLibraryClasspathContainerInitializer extends ScalaClasspathContainerInitializer("Scala Library") {
  val plugin = ScalaPlugin.plugin
  import plugin._

  override def entries = (library +: ScalaInstallation.platformInstallation.extraJars).map {libraryEntries}.to[Array]
}

class ScalaCompilerClasspathContainerInitializer extends ScalaClasspathContainerInitializer("Scala Compiler") {
  val plugin = ScalaPlugin.plugin
  import plugin._
  import ScalaInstallation.platformInstallation._

  override def entries = Array(libraryEntries(compiler))
}

abstract class ScalaClasspathContainerPage(id: String, name: String, title: String, desc: String) extends NewElementWizardPage(name) with IClasspathContainerPage {
  val fContainerEntryResult = JavaCore.newContainerEntry(new Path(id))

  setTitle(title)
  setDescription(desc)
  setImageDescriptor(JavaPluginImages.DESC_WIZBAN_ADD_LIBRARY)

  override def finish() = true

  override def getSelection(): IClasspathEntry = fContainerEntryResult

  override def setSelection(containerEntry: IClasspathEntry) {}

  override def createControl(parent: Composite) {
    val composite = new Composite(parent, SWT.NONE)
    setControl(composite)
  }
}

class ScalaCompilerClasspathContainerPage extends
  ScalaClasspathContainerPage(
    ScalaPlugin.plugin.scalaCompilerId,
    "ScalaCompilerContainerPage",
    "Scala Compiler Container",
    "Scala compiler container (bundled) version " + ScalaPlugin.plugin.scalaVer.unparse)

class ScalaLibraryClasspathContainerPage extends
  ScalaClasspathContainerPage(ScalaPlugin.plugin.scalaLibId,
    "ScalaLibraryContainerPage",
    "Scala Library Container",
    "Scala library container (bundled) version " + ScalaPlugin.plugin.scalaVer.unparse)
