/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.util.{ HashMap => JHashMap, Map => JMap }

import scala.concurrent.SyncVar

import org.eclipse.core.resources.{ IFile, IResource }
import org.eclipse.core.runtime.{ IProgressMonitor, IStatus }
import org.eclipse.jdt.core.{ IJavaElement, IJavaModelStatusConstants, IJavaProject, JavaModelException }
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.{ CompletionContext, CompletionProposal, CompletionRequestor, Flags, ICompilationUnit, IProblemRequestor, ITypeRoot, JavaCore, WorkingCopyOwner }
import org.eclipse.jdt.internal.compiler.env
import org.eclipse.jdt.internal.core.{
  BecomeWorkingCopyOperation, CompilationUnit => JDTCompilationUnit, CompilationUnitElementInfo, DefaultWorkingCopyOwner,
  JavaModelManager, JavaModelStatus, JavaProject, OpenableElementInfo, PackageFragment, SelectionRequestor }
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.widgets.Display

import scala.tools.nsc.interactive.Global
import scala.tools.nsc.util.{ BatchSourceFile, SourceFile }

import scala.tools.eclipse.{ ScalaPlugin, ScalaPresentationCompiler, ScalaSourceIndexer }
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
        val compiler = project.presentationCompiler
        val body = {
          val typed = new SyncVar[Either[compiler.Tree, Throwable]]
          compiler.askType(getSourceFile(info), reload, typed)
          typed.get match {
            case Left(tree) =>
              if (reload) {
                val file = getCorrespondingResource().asInstanceOf[IFile]
                val problems = compiler.problemsOf(file)
                val problemRequestor = getPerWorkingCopyInfo
                if (problemRequestor != null) {
                  try {
                    problemRequestor.beginReporting
                    problems.map(problemRequestor.acceptProblem(_))
                  } finally {
                    problemRequestor.endReporting
                  }
                }
              }
              tree
            case Right(thr) =>
              ScalaPlugin.plugin.logError("Failure in presentation compiler", thr)
              compiler.EmptyTree
          }
        }
      }
      
      reload = false
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

    val info = createElementInfo.asInstanceOf[ScalaCompilationUnitElementInfo]
    sFile = new BatchSourceFile(aFile, getBuffer(info).getCharacters) 
    reload = true
    openWhenClosed(info, monitor)
    null
  }
    
  override def codeSelect(offset : Int, length : Int, workingCopyOwner : WorkingCopyOwner) : Array[IJavaElement] = {
    val javaProject = getJavaProject().asInstanceOf[JavaProject]
    val environment = javaProject.newSearchableNameEnvironment(workingCopyOwner)
    
    val requestor = new SelectionRequestor(environment.nameLookup, this)
    val buffer = getBuffer()
    if (buffer != null) {
      val end = buffer.getLength()
      if (offset < 0 || length < 0 || offset + length > end )
        throw new JavaModelException(new JavaModelStatus(IJavaModelStatusConstants.INDEX_OUT_OF_BOUNDS))
  
      val engine = new ScalaSelectionEngine(environment, requestor, javaProject.getOptions(true))
      engine.select(this, offset, offset + length - 1)
    }
    
    val elements = requestor.getElements()
    if(elements.isEmpty)
      println("No selection")
    else
      for(e <- elements)
        println(e)
    elements
  }

  def codeComplete
    (cu : env.ICompilationUnit, unitToSkip : env.ICompilationUnit,
     position : Int,  requestor : CompletionRequestor, owner : WorkingCopyOwner, typeRoot : ITypeRoot) {
     codeComplete(cu, unitToSkip, position, requestor, owner, typeRoot, null) 
  }
    
  override def codeComplete
    (cu : env.ICompilationUnit, unitToSkip : env.ICompilationUnit,
     position : Int,  requestor : CompletionRequestor, owner : WorkingCopyOwner, typeRoot : ITypeRoot,
     monitor : IProgressMonitor) {

    val th = getTreeHolder
    import th._

    val pos = compiler.rangePos(getSourceFile, position, position, position)
    
    val completed = new SyncVar[Either[List[compiler.Member], Throwable]]
    compiler.askTypeCompletion(pos, completed)
    completed.get.left.toOption match {
      case Some(completions) =>
        for(completion <- completions)
          println(completion)
      case None =>
        println("No completions")
    }

    /*
    val context = new CompletionContext
    requestor.acceptContext(context)

    val proposal = CompletionProposal.create(CompletionProposal.FIELD_REF, position)
    proposal.setDeclarationSignature("I".toArray)
    proposal.setSignature("I".toArray)
    //proposal.setTypeName("type".toArray)
    proposal.setName("name".toArray)
    proposal.setCompletion("completion".toArray)
    proposal.setFlags(Flags.AccPublic)
    proposal.setReplaceRange(position, position)
    proposal.setTokenRange(0, 0)
    proposal.setRelevance(1)
    requestor.accept(proposal)
    */
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
