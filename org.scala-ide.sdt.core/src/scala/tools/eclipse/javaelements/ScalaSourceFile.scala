/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

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

  /** Schedule this compilation unit for reconciliation.
   */
  override def scheduleReconcile() = {
    this.reconcile(
        ICompilationUnit.NO_AST,
        false /* don't force problem detection */,
        null /* use primary owner */,
        null /* no progress monitor */);
  }

  override def reconcile(
      astLevel : Int,
      reconcileFlags : Int,
      workingCopyOwner : WorkingCopyOwner,
      monitor : IProgressMonitor) : org.eclipse.jdt.core.dom.CompilationUnit = {
    super.reconcile(ICompilationUnit.NO_AST, reconcileFlags, workingCopyOwner, monitor)
  }

  override def makeConsistent(
    astLevel : Int,
    resolveBindings : Boolean,
    reconcileFlags : Int,
    problems : JHashMap[_,_],
    monitor : IProgressMonitor) : org.eclipse.jdt.core.dom.CompilationUnit = {
    val info = createElementInfo.asInstanceOf[OpenableElementInfo]
    openWhenClosed(info, monitor)
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

  def getProblems : Array[IProblem] = withSourceFile { (src, compiler) =>
    val problems = compiler.problemsOf(this)
    if (problems.isEmpty) null else problems.toArray
  } (null)
  
  override def getType(name : String) : IType = new LazyToplevelClass(this, name)
}
