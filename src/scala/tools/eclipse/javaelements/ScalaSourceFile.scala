/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.util.{ HashMap => JHashMap, Map => JMap }

import org.eclipse.core.resources.{ IFile, IResource }
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.{ IBuffer, ICompilationUnit, IJavaElement, IType, WorkingCopyOwner }
import org.eclipse.jdt.internal.core.util.HandleFactory
import org.eclipse.jdt.internal.core.{ BufferManager, CompilationUnit => JDTCompilationUnit, OpenableElementInfo, PackageFragment }

import scala.tools.nsc.io.{ AbstractFile, VirtualFile }
import scala.tools.nsc.util.BatchSourceFile

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

  override def discardWorkingCopy {
    discard
    super.discardWorkingCopy
  }

  override def getProblemRequestor = getPerWorkingCopyInfo

  def getFile : AbstractFile = { 
    val res = getCorrespondingResource
    if (res != null)
      new EclipseFile(res.asInstanceOf[IFile])
    else
      new VirtualFile(getElementName)
  }

  def getCorrespondingElement(element : IJavaElement) : Option[IJavaElement] = {
    val name = element.getElementName
    getChildren.find(_.getElementName == name)
  }

  override def getType(name : String) : IType = {
    val tpe = super.getType(name)
    getCorrespondingElement(tpe).getOrElse(tpe).asInstanceOf[IType]
  }
}
