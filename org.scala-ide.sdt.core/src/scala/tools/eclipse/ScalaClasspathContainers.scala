/*
 * Copyright 2005-2010 LAMP/EPFL
 */

// $Id$

package scala.tools.eclipse

import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.ClasspathContainerInitializer
import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.jdt.ui.wizards.NewElementWizardPage
import org.eclipse.jdt.ui.wizards.IClasspathContainerPage
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite
import scala.tools.eclipse.logging.HasLogger

abstract class ScalaClasspathContainerInitializer(desc : String) extends ClasspathContainerInitializer with HasLogger {
  def entries : Array[IClasspathEntry]

  def initialize(containerPath : IPath, project : IJavaProject) = {
    logger.info(s"Initializing classpath container $desc: ${ScalaPlugin.plugin.libClasses}")
    logger.info(s"Initializing classpath container $desc with sources: ${ScalaPlugin.plugin.libSources}")

    JavaCore.setClasspathContainer(containerPath, Array(project), Array(new IClasspathContainer {
      def getPath = containerPath
      def getClasspathEntries = entries
      def getDescription = desc+" [" + scala.util.Properties.scalaPropOrElse("version.number", "(unknown)")+"]"
      def getKind = IClasspathContainer.K_SYSTEM
    }), null)
  }

  protected def libraryEntries(classes: IPath, sources: Option[IPath]): IClasspathEntry = {
    if(sources.isEmpty) logger.debug(s"No source attachements for ${classes.lastSegment()}")

    JavaCore.newLibraryEntry(classes, sources.orNull, null)
  }
}

class ScalaLibraryClasspathContainerInitializer extends ScalaClasspathContainerInitializer("Scala Library") {
  val plugin = ScalaPlugin.plugin
  import plugin._

  override def entries = Array(
    (libClasses, libSources),
    (reflectClasses, reflectSources),
    // modules:
    (actorsClasses, actorsSources),
    (swingClasses, swingSources)
  ).flatMap { case (c, s) => c map { classes => libraryEntries(classes, s) }}
}

class ScalaCompilerClasspathContainerInitializer extends ScalaClasspathContainerInitializer("Scala Compiler") {
  val plugin = ScalaPlugin.plugin
  import plugin._

  override def entries = Array(libraryEntries(compilerClasses.get, compilerSources))
}

abstract class ScalaClasspathContainerPage(id : String, name : String, title : String, desc : String) extends NewElementWizardPage(name) with IClasspathContainerPage {
  val fContainerEntryResult = JavaCore.newContainerEntry(new Path(id))

  setTitle(title)
  setDescription(desc)
  setImageDescriptor(JavaPluginImages.DESC_WIZBAN_ADD_LIBRARY)

  def finish() = true

  def getSelection() : IClasspathEntry = fContainerEntryResult

  def setSelection(containerEntry : IClasspathEntry) {}

  def createControl(parent : Composite) {
    val composite = new Composite(parent, SWT.NONE)
    setControl(composite)
  }
}

class ScalaCompilerClasspathContainerPage extends
  ScalaClasspathContainerPage(
    ScalaPlugin.plugin.scalaCompilerId,
    "ScalaCompilerContainerPage",
    "Scala Compiler Container",
    "Scala compiler container")

class ScalaLibraryClasspathContainerPage extends
  ScalaClasspathContainerPage(ScalaPlugin.plugin.scalaLibId,
    "ScalaLibraryContainerPage",
    "Scala Library Container",
    "Scala library container")