/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.wizards;

import org.eclipse.ui.wizards.newresource._
import org.eclipse.ui._
import org.eclipse.ui.ide._
import org.eclipse.jface.wizard._
import org.eclipse.jface.viewers._
import org.eclipse.jdt.core._
import org.eclipse.swt.widgets._
import org.eclipse.swt.layout._
import org.eclipse.swt.SWT
import org.eclipse.core.resources._
import org.eclipse.core.runtime._
import org.eclipse.jdt.internal.core._

import scala.collection.jcl._
import scala.collection.mutable.ArrayBuffer

/** Simple "New Project" Wizard dialog.  Will create accept a project name, and set up the new scala project. */
class NewProjectWizard extends BasicNewProjectResourceWizard {
  override def addPages = {
    super.addPages
    getStartingPage.setDescription("Create a new Scala project")
    getStartingPage.setTitle("New Scala Project")
    setWindowTitle("New Scala Project")
  }
  override def performFinish : Boolean = try {
    if (!super.performFinish) return false
    val desc = getNewProject.getDescription
    val natures = JavaCore.NATURE_ID :: ScalaPlugin.plugin.natureId :: desc.getNatureIds.toList
    desc.setNatureIds(natures.reverse.toArray)
    getNewProject.setDescription(desc, null)
    
    ScalaPlugin.plugin.javaProject(getNewProject) match {
      case Some(project) =>
        val scpes = project.getRawClasspath.filter(_.getEntryKind == IClasspathEntry.CPE_SOURCE)
        if (scpes.length == 1 && scpes(0).getPath == project.getPath) {
          val src = getNewProject.getFolder("src")
          if (!src.exists()) {
            src.create(true, true, null);
          }
          val sourceEntry = JavaCore.newSourceEntry(src.getFullPath)
          val buf = new ArrayBuffer[IClasspathEntry]
          //remove project from classpath, add "src" directory
          buf ++= project.getRawClasspath.map {
            entry => 
              if(entry.getPath equals project.getPath) {
                sourceEntry
              } else {
                entry
              }
          }
          project.setRawClasspath(buf.toArray, null)
          project.save(null, true)
        }
        
      case None =>
        ScalaPlugin.plugin.logError("Java project cannot be None!", new RuntimeException("Java project was none!"))
    } 
    return true
  } catch {
    case ex => ScalaPlugin.plugin.logError(ex); return false
  }
}
