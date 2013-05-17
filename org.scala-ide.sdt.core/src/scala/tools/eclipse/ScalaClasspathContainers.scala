/*
 * Copyright 2005-2010 LAMP/EPFL
 */

// $Id$

package scala.tools.eclipse

import org.eclipse.core.runtime.{ IPath, Path }
import org.eclipse.jdt.core.{ ClasspathContainerInitializer, IClasspathContainer, IJavaProject, JavaCore, IClasspathEntry }
import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.jdt.ui.wizards.{ NewElementWizardPage, IClasspathContainerPage }
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite

abstract class ScalaClasspathContainerInitializer(desc : String) extends ClasspathContainerInitializer {
  def entries : Array[IClasspathEntry]

  def initialize(containerPath : IPath, project : IJavaProject) =
    JavaCore.setClasspathContainer(containerPath, Array(project), Array(new IClasspathContainer {
      def getPath = containerPath
      def getClasspathEntries = entries
      def getDescription = desc+" [" + scala.util.Properties.scalaPropOrElse("version.number", "(unknown)")+"]"
      def getKind = IClasspathContainer.K_SYSTEM
    }), null)
}

class ScalaLibraryClasspathContainerInitializer extends ScalaClasspathContainerInitializer("Scala Library") {
  val plugin = ScalaPlugin.plugin
  import plugin._

  val entries = List(
    libClasses.map(classes => JavaCore.newLibraryEntry(classes, libSources.getOrElse(null), null)),
    dbcClasses.map(classes => JavaCore.newLibraryEntry(classes, dbcSources.getOrElse(null), null)),
    swingClasses.map(classes => JavaCore.newLibraryEntry(classes, swingSources.getOrElse(null), null)),
    actorsClasses.map(classes => JavaCore.newLibraryEntry(classes, actorsSources.getOrElse(null), null)),
    reflectClasses.map(classes => JavaCore.newLibraryEntry(classes, reflectSources.getOrElse(null), null))
  ).flatten.toArray
}

class ScalaCompilerClasspathContainerInitializer extends ScalaClasspathContainerInitializer("Scala Compiler") {
  val plugin = ScalaPlugin.plugin
  import plugin._

  val entries = Array(
    JavaCore.newLibraryEntry(compilerClasses.get, compilerSources.getOrElse(null), null)
  )
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