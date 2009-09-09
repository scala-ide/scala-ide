/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.util.{ HashMap => JHashMap, Map => JMap }

import scala.concurrent.SyncVar

import org.eclipse.core.resources.{ IFile, IResource }
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.{
  CompletionContext, CompletionProposal, CompletionRequestor, Flags, ICompilationUnit, IJavaElement, IJavaModelStatusConstants,
  IProblemRequestor, ITypeRoot, JavaCore, JavaModelException, WorkingCopyOwner }
import org.eclipse.jdt.internal.compiler.env
import org.eclipse.jdt.internal.core.{ CompilationUnitElementInfo, JavaModelStatus, JavaProject, Openable, OpenableElementInfo }
import org.eclipse.swt.graphics.Image

import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.{ BatchSourceFile, SourceFile }

import scala.tools.eclipse.{ ScalaPlugin, ScalaPresentationCompiler, ScalaSourceIndexer }

abstract class TreeHolder {
  val compiler : ScalaPresentationCompiler
  val body : compiler.Tree
}

trait ScalaCompilationUnit extends Openable with env.ICompilationUnit with ScalaElement with ImageSubstituter {
  val project = ScalaPlugin.plugin.getScalaProject(getJavaProject.getProject)
  lazy val aFile = getFile
  var sFile : BatchSourceFile = null
  var treeHolder : TreeHolder = null
  var reload = false
  
  def getFile : AbstractFile
  
  def getTreeHolder : TreeHolder = {
    if (treeHolder == null) {

      treeHolder = new TreeHolder {
        val compiler = project.presentationCompiler
        val body = {
          val typed = new SyncVar[Either[compiler.Tree, Throwable]]
          compiler.askType(getSourceFile, reload, typed)
          typed.get match {
            case Left(tree) =>
              if (reload) {
                val file = getCorrespondingResource.asInstanceOf[IFile]
                val problems = compiler.problemsOf(file)
                val problemRequestor = getProblemRequestor
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
  
  def getSourceFile : SourceFile = {
    if (sFile == null)
      sFile = new BatchSourceFile(aFile, getBuffer.getCharacters) 
    sFile
  }

  def getProblemRequestor : IProblemRequestor = null

  override def buildStructure(info : OpenableElementInfo, pm : IProgressMonitor, newElements : JMap[_, _], underlyingResource : IResource) : Boolean = {
    val th = getTreeHolder
    import th._

    if (body == null || body.isEmpty) {
      info.setIsStructureKnown(false)
      return info.isStructureKnown
    }
    
    val sourceLength = getBuffer.getLength
    new compiler.StructureBuilderTraverser(this, info, newElements.asInstanceOf[JMap[AnyRef, AnyRef]], sourceLength).traverse(body)
    
    info match {
      case cuei : CompilationUnitElementInfo =>
        cuei.setSourceLength(sourceLength)
      case _ =>
    }

    info.setIsStructureKnown(true)
    info.isStructureKnown
  }
  
  def addToIndexer(indexer : ScalaSourceIndexer) {
    val th = getTreeHolder 
    import th._

    if (body != null)
      new compiler.IndexBuilderTraverser(indexer).traverse(body)
  }
  
  override def codeSelect(cu : env.ICompilationUnit, offset : Int, length : Int, workingCopyOwner : WorkingCopyOwner) : Array[IJavaElement] = {
    val javaProject = getJavaProject.asInstanceOf[JavaProject]
    val environment = javaProject.newSearchableNameEnvironment(workingCopyOwner)
    
    val requestor = new ScalaSelectionRequestor(environment.nameLookup, this)
    val buffer = getBuffer
    if (buffer != null) {
      val end = buffer.getLength
      if (offset < 0 || length < 0 || offset + length > end )
        throw new JavaModelException(new JavaModelStatus(IJavaModelStatusConstants.INDEX_OUT_OF_BOUNDS))
  
      val engine = new ScalaSelectionEngine(environment, requestor, javaProject.getOptions(true))
      engine.select(cu, offset, offset + length - 1)
    }
    
    val elements = requestor.getElements
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
