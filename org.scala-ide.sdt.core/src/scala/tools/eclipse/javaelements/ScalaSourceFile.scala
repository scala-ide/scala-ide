/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package javaelements

import java.util.{ HashMap => JHashMap, Map => JMap }
import org.eclipse.core.resources.{ IFile, IResource }
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.{ IBuffer, ICompilationUnit, IJavaElement, IType, WorkingCopyOwner }
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.core.util.HandleFactory
import org.eclipse.jdt.internal.core.{ BufferManager, CompilationUnit => JDTCompilationUnit, OpenableElementInfo, PackageFragment }
import org.eclipse.jdt.internal.corext.util.JavaModelUtil
import org.eclipse.swt.widgets.Display
import scala.tools.nsc.io.{ AbstractFile, VirtualFile }
import scala.tools.eclipse.contribution.weaving.jdt.IScalaSourceFile
import scala.tools.eclipse.util.EclipseFile
import scala.tools.eclipse.util.Tracer
import scala.tools.eclipse.util.Defensive
import org.eclipse.core.runtime.jobs.Job

object ScalaSourceFile {
  val handleFactory = new HandleFactory
  
  def createFromPath(path : String) : Option[ScalaSourceFile] = {
    if (!path.endsWith(".scala"))
      None
    else
      handleFactory.createOpenable(path, null) match {
        case ssf : ScalaSourceFile => Some(ssf)
        case _ => None
      }
  }
}

class ScalaSourceFile(fragment : PackageFragment, elementName: String, workingCopyOwner : WorkingCopyOwner) 
  extends JDTCompilationUnit(fragment, elementName, workingCopyOwner) with ScalaCompilationUnit with IScalaSourceFile {

  override def getMainTypeName : Array[Char] =
    getElementName.substring(0, getElementName.length - ".scala".length).toCharArray()
  
  /**
   * !! reconcile can take time (creation of presentation compiler, previous task,...)
   *    => Don't run it in the Display Thread to avoid UI freeze
   *      (unlike what was done with removed method 'scheduleReconcile')
   */
  override def reconcile(
      astLevel : Int,
      reconcileFlags : Int,
      workingCopyOwner : WorkingCopyOwner,
      monitor : IProgressMonitor) : org.eclipse.jdt.core.dom.CompilationUnit = Tracer.timeOf("reconcile of " + file.path){
    ScalaPlugin.plugin.reconcileListeners.triggerBeforeReconcile(this, monitor, workingCopyOwner)
    val b = super.reconcile(ICompilationUnit.NO_AST, reconcileFlags, workingCopyOwner, monitor)
    ScalaPlugin.plugin.reconcileListeners.triggerAfterReconcile(this, monitor, workingCopyOwner)
    b
  }

  override def makeConsistent(
    astLevel : Int,
    resolveBindings : Boolean,
    reconcileFlags : Int,
    problems : JHashMap[_,_],
    monitor : IProgressMonitor) : org.eclipse.jdt.core.dom.CompilationUnit = {
    Defensive.askRunOutOfMain("makeConsistent", Job.INTERACTIVE) {
      val info = createElementInfo.asInstanceOf[OpenableElementInfo]
      openWhenClosed(info, monitor)
    }
    null
  }

  override def codeSelect(offset : Int, length : Int, workingCopyOwner : WorkingCopyOwner) : Array[IJavaElement] =
    codeSelect(this, offset, length, workingCopyOwner)

  override def getProblemRequestor = getPerWorkingCopyInfo

  override lazy val file : AbstractFile = { 
    val res = try { getCorrespondingResource } catch { case _ => null }
    res match {
      case f : IFile => new EclipseFile(f)
      case _ => new VirtualFile(getElementName, getPath.toString)
    }
  }

  def getProblems : Array[IProblem] = withSourceFile { (sourceFile, compiler) =>
    compiler.body(sourceFile)
    val problems = compiler.problemsOf(this)
    if (problems.isEmpty) null else problems.toArray
  }
  
  def getCorrespondingElement(element : IJavaElement) : Option[IJavaElement] = {
    if (!validateExistence(resource).isOK)
      None
    else {
      val name = element.getElementName
      val tpe = element.getElementType
      getChildren.find(e => e.getElementName == name && e.getElementType == tpe)
    }
  }

  override def getType(name : String) : IType = {
    val tpe = super.getType(name)
    getCorrespondingElement(tpe).getOrElse(tpe).asInstanceOf[IType]
  }
  
  // override because super implementation return null if getSourceElementAt(pos) == this.
  // But returning null prevent finding the typename required to set a workable breakpoint
  // TODO check that this override has no bad side-effects (I suppose returning null of parent implementation was done to secure some stuff 
  override def getElementAt(pos : Int) : IJavaElement = getSourceElementAt(pos)
}
