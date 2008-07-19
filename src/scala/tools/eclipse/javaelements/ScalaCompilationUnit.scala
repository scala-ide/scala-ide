/*
 * Copyright 2005-2008 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.util.{ HashMap, Map }

import org.eclipse.core.resources.{ IFile, IResource }
import org.eclipse.core.runtime.{ IProgressMonitor, IStatus, Platform }
import org.eclipse.core.runtime.content.IContentTypeSettings
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.{ ICompilationUnit, IProblemRequestor, JavaCore, WorkingCopyOwner }
import org.eclipse.jdt.internal.core.{
  BecomeWorkingCopyOperation, CompilationUnit, CompilationUnitElementInfo, DefaultWorkingCopyOwner,
  JavaModelManager, JavaModelStatus, OpenableElementInfo, PackageFragment }
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Display

class ScalaCompilationUnitInfo extends CompilationUnitElementInfo

class ScalaCompilationUnit(fragment : PackageFragment, elementName: String, workingCopyOwner : WorkingCopyOwner)
  extends CompilationUnit(fragment, elementName, workingCopyOwner) with ScalaElement with ImageSubstituter with ScalaStructureBuilder {

  val plugin = ScalaPlugin.plugin
  val proj = plugin.projectSafe(getResource.getProject).get
  val compiler = proj.compiler0
    
  def this(file : IFile) =
    this(JDTUtils.getParentPackage(file).asInstanceOf[PackageFragment], file.getName, ScalaWorkingCopyOwner)
    
  override def getMainTypeName : Array[Char] =
    elementName.substring(0, elementName.length - ".scala".length).toCharArray()

  override def generateInfos(info : Object, newElements : HashMap[_, _],  monitor : IProgressMonitor) = {
    val sinfo = if (info.isInstanceOf[ScalaCompilationUnitInfo]) info else new ScalaCompilationUnitInfo 
    super.generateInfos(sinfo, newElements, monitor);
  }
  
  override def buildStructure(info : OpenableElementInfo, pm : IProgressMonitor, newElements : Map[_, _], underlyingResource : IResource) : Boolean = {
    val fileOpt = proj.fileSafe(getCorrespondingResource.asInstanceOf[IFile])
    if (fileOpt.isEmpty)  
      return false
    val file = fileOpt.get
    val root = file.outlineTrees
 
    if (!isWorkingCopy) {
      val status = validateCompilationUnit(underlyingResource)
      if (!status.isOK) throw newJavaModelException(status)
    }

    // prevents reopening of non-primary working copies (they are closed when
    // they are discarded and should not be reopened)
    if (!isPrimary && getPerWorkingCopyInfo == null)
      throw newNotPresentException

    new StructureBuilderTraverser(info.asInstanceOf[ScalaCompilationUnitInfo], newElements.asInstanceOf[Map[AnyRef, AnyRef]]).traverseTrees(root)
    
    val unitInfo = info.asInstanceOf[ScalaCompilationUnitInfo]
    unitInfo.setIsStructureKnown(true)
    unitInfo.isStructureKnown
  }

  override def isPrimary = owner eq ScalaWorkingCopyOwner

  override def createElementInfo : Object = new ScalaCompilationUnitInfo
  
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

  override def validateCompilationUnit(resource : IResource) : IStatus = JavaModelStatus.VERIFIED_OK

  override def reconcile(
      astLevel : Int,
      reconcileFlags : Int,
      workingCopyOwner : WorkingCopyOwner,
      monitor : IProgressMonitor) : org.eclipse.jdt.core.dom.CompilationUnit = {
    if (!isWorkingCopy()) return null // Reconciling is not supported on non working copies
    val wco = if (workingCopyOwner != null) workingCopyOwner else ScalaWorkingCopyOwner
    
    val op = new ScalaReconcileWorkingCopyOperation(this, astLevel == AST.JLS3, astLevel, true, workingCopyOwner)
    op.runOperation(monitor)
    return op.ast
  }

  override def makeConsistent(
    astLevel : Int,
    resolveBindings : Boolean,
    reconcileFlags : Int,
    problems : HashMap[_,_],
    monitor : IProgressMonitor) : org.eclipse.jdt.core.dom.CompilationUnit = {
    openWhenClosed(createElementInfo(), monitor)
    null
  }
  
  override def getHandleIdentifier : String = {
    // See https://bugs.eclipse.org/bugs/show_bug.cgi?id=74426
    val callerName = (new RuntimeException).getStackTrace()(1).getClassName
    val deletionClass = "org.eclipse.jdt.internal.corext.refactoring.changes.DeleteSourceManipulationChange"
    // are we being called in the context of a delete operation?
    if (callerName == deletionClass) {
      val file = getCorrespondingResource.asInstanceOf[IFile]
      ScalaCompilationUnitManager.removeFileFromModel(file)
      
      // Create the substitute compilation unit without tripping name validation checks 
      val project = JavaCore.create(file.getProject)
      val pkg = JavaModelManager.determineIfOnClasspath(file, project).asInstanceOf[PackageFragment]
      val cu = new CompilationUnit(pkg, file.getName, DefaultWorkingCopyOwner.PRIMARY)
      // Make the compilation unit appear to be a working copy so that the name validation
      // component of the existence check isn't tripped
      JavaModelManager.getJavaModelManager.getPerWorkingCopyInfo(cu, true, false, null)
      
      return cu.getHandleIdentifier
    }
    
    super.getHandleIdentifier
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
