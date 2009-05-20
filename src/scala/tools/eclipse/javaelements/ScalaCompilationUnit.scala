/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.util.{ HashMap => JHashMap, Map => JMap }

import org.eclipse.core.resources.{ IFile, IResource }
import org.eclipse.core.runtime.{ IProgressMonitor, IStatus }
import org.eclipse.jdt.core.{ IJavaElement, IJavaProject, JavaModelException }
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.{ ICompilationUnit, IProblemRequestor, JavaCore, WorkingCopyOwner }
import org.eclipse.jdt.internal.core.{
  BecomeWorkingCopyOperation, CompilationUnit => JDTCompilationUnit, CompilationUnitElementInfo, DefaultWorkingCopyOwner,
  JavaModelManager, JavaModelStatus, OpenableElementInfo, PackageFragment }
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Display

import scala.tools.nsc.util.{ NoPosition, Position }

import scala.tools.eclipse.ScalaFile

class ScalaCompilationUnitInfo extends CompilationUnitElementInfo

class ScalaCompilationUnit(fragment : PackageFragment, elementName: String, workingCopyOwner : WorkingCopyOwner)
  extends JDTCompilationUnit(fragment, elementName, workingCopyOwner) with ScalaElement with ImageSubstituter with ScalaStructureBuilder with ScalaIndexBuilder {

  val plugin = ScalaPlugin.plugin
  val proj = plugin.projectSafe(getResource.getProject).get
  import proj.compiler._
  
  override def getMainTypeName : Array[Char] =
    elementName.substring(0, elementName.length - ".scala".length).toCharArray()

  override def generateInfos(info : Object, newElements : JHashMap[_, _],  monitor : IProgressMonitor) = {
    val sinfo = if (info.isInstanceOf[ScalaCompilationUnitInfo]) info else new ScalaCompilationUnitInfo 
    super.generateInfos(sinfo, newElements, monitor);
  }
  
  override def buildStructure(info : OpenableElementInfo, pm : IProgressMonitor, newElements : JMap[_, _], underlyingResource : IResource) : Boolean = {
    val unitInfo = info.asInstanceOf[ScalaCompilationUnitInfo]
    val (root, file) = try { 
      val resource = getCorrespondingResource.asInstanceOf[IFile]
      val file0 = new ScalaFile(resource)
      // TODO reinstate
      //(fileOpt0.map(_.outlineTrees).getOrElse(Nil), fileOpt0)
      (Nil, file0)
    } catch {
      case ex : JavaModelException => (Nil, null)
    }
    
    if (root.isEmpty) {
      unitInfo.setIsStructureKnown(false)
      return unitInfo.isStructureKnown
    }
    
    if (!isWorkingCopy) {
      val status = validateCompilationUnit(underlyingResource)
      if (!status.isOK) throw newJavaModelException(status)
    }

    // prevents reopening of non-primary working copies (they are closed when
    // they are discarded and should not be reopened)
    if (!isPrimary && getPerWorkingCopyInfo == null)
      throw newNotPresentException

    val sourceLength = file.length
    new StructureBuilderTraverser(unitInfo, newElements.asInstanceOf[JMap[AnyRef, AnyRef]], sourceLength).traverseTrees(root)
    
    unitInfo.setSourceLength(sourceLength)
    unitInfo.setIsStructureKnown(true)
    unitInfo.isStructureKnown
  }
  
  def addToIndexer(indexer : ScalaSourceIndexer) {
    val file = new ScalaFile(getCorrespondingResource.asInstanceOf[IFile])
    // TODO reinstate
    // val root = fileOpt.map(_.outlineTrees).getOrElse(Nil)
    val root = Nil
    if (!root.isEmpty)
      new IndexBuilderTraverser(indexer).traverseTrees(root)
  }
  
  override def createElementInfo : Object = new ScalaCompilationUnitInfo
  
  override def getElementAt(position : Int) : IJavaElement = {
    try {
      val e = super.getElementAt(position)
      if (e eq this) null else e
    } catch {
      case ex : ClassCastException => null
    }
  }
  
  /**
   * @see ICompilationUnit#getWorkingCopy(WorkingCopyOwner, IProblemRequestor, IProgressMonitor)
  */
  override def getWorkingCopy(workingCopyOwner : WorkingCopyOwner, problemRequestor : IProblemRequestor, monitor : IProgressMonitor) : ICompilationUnit = {
    if (!isPrimary)
      return this
    
    val manager = JavaModelManager.getJavaModelManager
    val workingCopy = new ScalaCompilationUnit(getParent.asInstanceOf[PackageFragment], getElementName, workingCopyOwner)
    
    val perWorkingCopyInfo = 
      manager.getPerWorkingCopyInfo(workingCopy, false/*don't create*/, true/*record usage*/, null/*not used since don't create*/)
    if (perWorkingCopyInfo != null) {
      return perWorkingCopyInfo.getWorkingCopy(); // return existing handle instead of the one created above
    }
    val op = new BecomeWorkingCopyOperation(workingCopy, problemRequestor)
    op.runOperation(monitor)
    workingCopy
  }

  override def validateCompilationUnit(resource : IResource) : IStatus = super.validateCompilationUnit(resource)

  override def reconcile(
      astLevel : Int,
      reconcileFlags : Int,
      workingCopyOwner : WorkingCopyOwner,
      monitor : IProgressMonitor) : org.eclipse.jdt.core.dom.CompilationUnit = {
    if (!isWorkingCopy()) return null // Reconciling is not supported on non working copies
    
    val op = new ScalaReconcileWorkingCopyOperation(this, astLevel == AST.JLS3, astLevel, true, workingCopyOwner)
    op.runOperation(monitor)
    return op.ast
  }

  override def makeConsistent(
    astLevel : Int,
    resolveBindings : Boolean,
    reconcileFlags : Int,
    problems : JHashMap[_,_],
    monitor : IProgressMonitor) : org.eclipse.jdt.core.dom.CompilationUnit = {
    openWhenClosed(createElementInfo(), monitor)
    null
  }
  
  override def mapLabelImage(original : Image) = super.mapLabelImage(original)
  override def replacementImage = {
    val file = getCorrespondingResource.asInstanceOf[IFile]
    if(file == null)
      null
    else {
      import ScalaImages.{ SCALA_FILE, EXCLUDED_SCALA_FILE }
      val project = JavaCore.create(file.getProject)
      if(project.isOnClasspath(file)) SCALA_FILE else EXCLUDED_SCALA_FILE
    }
  }
}
