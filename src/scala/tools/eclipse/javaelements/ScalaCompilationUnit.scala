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

import scala.tools.nsc.interactive.Global
import scala.tools.nsc.util.{ BatchSourceFile, SourceFile }

import scala.tools.eclipse.ScalaFile
import scala.tools.eclipse.util.EclipseFile

class ScalaCompilationUnitElementInfo extends CompilationUnitElementInfo

abstract class TreeHolder {
  val compiler : ScalaPresentationCompiler
  val body : compiler.Tree
}

class ScalaCompilationUnit(fragment : PackageFragment, elementName: String, workingCopyOwner : WorkingCopyOwner)
  extends JDTCompilationUnit(fragment, elementName, workingCopyOwner) with ScalaElement with ImageSubstituter {

  val project = ScalaPlugin.plugin.getScalaProject(getResource.getProject)
  lazy val aFile = new EclipseFile(getCorrespondingResource.asInstanceOf[IFile])
  var sFile : BatchSourceFile = null
  var treeHolder : TreeHolder = null
  var reload = false
  
  def getTreeHolder(info : OpenableElementInfo) : TreeHolder = {
    if (treeHolder == null) {
      treeHolder = new TreeHolder {
        val compiler = project.compiler
        val body = compiler.loadTree(getSourceFile(info), reload)
        reload = false
      }
    }
    
    treeHolder
  }
  
  def getTreeHolder : TreeHolder = getTreeHolder(createElementInfo.asInstanceOf[ScalaCompilationUnitElementInfo])

  def discard {
    if (treeHolder != null) {
      val th = treeHolder
      import th._

      compiler.removeUnitOf(sFile)
      treeHolder = null
      sFile = null
    }
  }
  
  override def close {
    discard
    super.close
  }
  
  override def discardWorkingCopy {
    discard
    super.discardWorkingCopy
  }
  
  override def getMainTypeName : Array[Char] =
    elementName.substring(0, elementName.length - ".scala".length).toCharArray()

  override def generateInfos(info : Object, newElements : JHashMap[_, _],  monitor : IProgressMonitor) = {
    val sinfo = if (info.isInstanceOf[ScalaCompilationUnitElementInfo]) info else new ScalaCompilationUnitElementInfo 
    super.generateInfos(sinfo, newElements, monitor);
  }
    
  def getBuffer(info : OpenableElementInfo) = {
    val buffer = getBufferManager.getBuffer(this)
    if (buffer != null)
      buffer
    else
      openBuffer(null, info)
  }
    
  def getSourceFile : SourceFile = getSourceFile(createElementInfo.asInstanceOf[ScalaCompilationUnitElementInfo])
  
  def getSourceFile(info : OpenableElementInfo) : SourceFile = {
    if (sFile == null)
      sFile = new BatchSourceFile(aFile, getBuffer(info).getCharacters) 
    else
      sFile.setContent(getBuffer(info).getCharacters)
    sFile
  }

  override def buildStructure(info : OpenableElementInfo, pm : IProgressMonitor, newElements : JMap[_, _], underlyingResource : IResource) : Boolean = {
    val th = getTreeHolder(info)
    import th._

    val unitInfo = info.asInstanceOf[ScalaCompilationUnitElementInfo]

    if (body == null || body.isEmpty) {
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

    val sourceLength = aFile.sizeOption.get
    new compiler.StructureBuilderTraverser(this, unitInfo, newElements.asInstanceOf[JMap[AnyRef, AnyRef]], sourceLength).traverse(body)
    
    unitInfo.setSourceLength(sourceLength)
    unitInfo.setIsStructureKnown(true)
    unitInfo.isStructureKnown
  }
  
  def addToIndexer(indexer : ScalaSourceIndexer) {
    val th = getTreeHolder 
    import th._

    if (body != null)
      new compiler.IndexBuilderTraverser(indexer).traverse(body)
  }
  
  override def createElementInfo : Object = new ScalaCompilationUnitElementInfo
  
  override def reconcile(
      astLevel : Int,
      reconcileFlags : Int,
      workingCopyOwner : WorkingCopyOwner,
      monitor : IProgressMonitor) : org.eclipse.jdt.core.dom.CompilationUnit = {
    super.reconcile(ICompilationUnit.NO_AST, reconcileFlags & ~ICompilationUnit.FORCE_PROBLEM_DETECTION, workingCopyOwner, monitor)
  }

  override def makeConsistent(
    astLevel : Int,
    resolveBindings : Boolean,
    reconcileFlags : Int,
    problems : JHashMap[_,_],
    monitor : IProgressMonitor) : org.eclipse.jdt.core.dom.CompilationUnit = {
    treeHolder = null
    reload = true
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
      val javaProject = JavaCore.create(project.underlying)
      if(javaProject.isOnClasspath(file)) SCALA_FILE else EXCLUDED_SCALA_FILE
    }
  }
}
