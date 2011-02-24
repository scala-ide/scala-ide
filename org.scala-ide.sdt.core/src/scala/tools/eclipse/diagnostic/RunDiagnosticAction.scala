package scala.tools.eclipse.diagnostic

import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.jdt.core.JavaModelException
import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.resources.{ IProject }
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.jface.action.IAction
import org.eclipse.jface.viewers.{ ISelection, IStructuredSelection }
import org.eclipse.ui.{ IObjectActionDelegate, IWorkbenchPart }

import org.eclipse.contribution.jdt.preferences.{ WeavingStateConfigurer, WeavingStateConfigurerUI }

import scala.tools.eclipse.ScalaPlugin.plugin
import scala.tools.eclipse.javaelements.JDTUtils


class RunDiagnosticAction extends IObjectActionDelegate {

  private var selectionOption: Option[ISelection] = None
	
	def selectionChanged(action: IAction, selection: ISelection) { this.selectionOption = Option(selection) }

  private def selectionToProject(selElem: Object): Option[IProject] = selElem match {
    case project: IProject => Some(project)
    case adaptable: IAdaptable => Option(adaptable.getAdapter(classOf[IProject]).asInstanceOf[IProject])
    case _ => None
  }
  
  def run(action: IAction) { 
    for {
      selection <- selectionOption
      if selection.isInstanceOf[IStructuredSelection]
      selElem <- selection.asInstanceOf[IStructuredSelection].toArray
      project <- selectionToProject(selElem)
    } showDialog(project)
  }
  
  def showDialog(project: IProject) {
    plugin check {
      println("*** Max memory: " + Runtime.getRuntime.maxMemory / 1048576 + " mb")
//      println("classpath: " + getClasspathJars(project).mkString("\n"));
      val scalaLibItem = getClasspathJars(project).find(_ contains "scala-library")
      println("*** scala library jar path: " + scalaLibItem)
      
      val configger = new WeavingStateConfigurer
      println("weaver version info:" + configger.getWeaverVersionInfo)
      println("current weaving state: " + configger.isWeaving)
      MessageDialog.openInformation(null, "JDT aspect weaving",
           "JDT aspect weaving enabled: " + configger.isWeaving)
      
      if (!configger.isWeaving) {
        val configUI = new WeavingStateConfigurerUI()
        println("Result of ask to toggle weaving: " + configUI.ask())
      }
    }
  } 
  
  def setActivePart(action: IAction, targetPart: IWorkbenchPart) {}
	
  def getClasspathJars(project: IProject): List[String] = {
    try {
      if (!plugin.isScalaProject(project)) Nil
      else {
        val jp = JavaCore.create(project)
        jp.getResolvedClasspath(true).filter(_.getEntryKind == IClasspathEntry.CPE_LIBRARY).toList.
          map(_.getPath.toString)
      }
    } catch { 
      case e: JavaModelException => e.printStackTrace(); Nil
    }
  }  
}