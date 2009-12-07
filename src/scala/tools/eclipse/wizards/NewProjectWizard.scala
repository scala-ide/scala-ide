/*
 * Copyright 2005-2010 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse.wizards

import scala.collection.mutable.ArrayBuffer

import org.eclipse.jdt.core.{ IClasspathEntry, JavaCore }
import org.eclipse.ui.wizards.newresource.BasicNewProjectResourceWizard

import scala.tools.eclipse.ScalaPlugin

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
    
    val project = JavaCore.create(getNewProject)
    val scpes = project.getRawClasspath.filter(_.getEntryKind == IClasspathEntry.CPE_SOURCE)
    if (scpes.length == 1 && scpes(0).getPath == project.getPath) {
      val src = getNewProject.getFolder("src")
      if (!src.exists())
        src.create(true, true, null)
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
    
    true
  } catch {
    case ex => ScalaPlugin.plugin.logError(ex)
    false
  }
}
