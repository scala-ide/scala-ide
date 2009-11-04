/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.util.{ Map => JMap }

import scala.concurrent.SyncVar

import org.eclipse.core.resources.{ IFile, IResource }
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.{
  BufferChangedEvent, CompletionRequestor, IBufferChangedListener, IJavaElement, IJavaModelStatusConstants,
  IProblemRequestor, ITypeRoot, JavaCore, JavaModelException, WorkingCopyOwner }
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.compiler.env
import org.eclipse.jdt.internal.core.{
  CompilationUnitElementInfo, DefaultWorkingCopyOwner, JavaModelStatus, JavaProject, Openable,
  OpenableElementInfo, SearchableEnvironment }
import org.eclipse.jdt.internal.core.search.matching.{ MatchLocator, PossibleMatch }

import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.{ BatchSourceFile, SourceFile }

import scala.tools.eclipse.contribution.weaving.jdt.{ IScalaCompilationUnit, IScalaWordFinder }

import scala.tools.eclipse.{ ScalaPlugin, ScalaPresentationCompiler, ScalaSourceIndexer }

abstract class TreeHolder {
  val compiler : ScalaPresentationCompiler
  val body : compiler.Tree
  val problems : List[IProblem]
}

trait ScalaCompilationUnit extends Openable with env.ICompilationUnit with ScalaElement with IScalaCompilationUnit with IBufferChangedListener {
  val project = ScalaPlugin.plugin.getScalaProject(getJavaProject.getProject)

  private lazy val aFile = getFile
  private var sFile : BatchSourceFile = null
  private var treeHolder : TreeHolder = null
  
  def getFile : AbstractFile
  
  def getProblems : Array[IProblem] =
    synchronized {
      if (treeHolder != null && !treeHolder.problems.isEmpty) treeHolder.problems.toArray else null
    }
  
  def getTreeHolder : TreeHolder = {
    // Obtaining a reference to the resource must be done outside
    // of sync to avoid a potential deadlock wrt discard
    val file = getCorrespondingResource.asInstanceOf[IFile]
    val sourceFile = getSourceFile
    
    val (result, changed) =
      synchronized {
        if (treeHolder != null) 
          (treeHolder, false)
        else {
          treeHolder = new TreeHolder {
            val compiler = project.presentationCompiler
            val (body, problems) = {
              val typed = new SyncVar[Either[compiler.Tree, Throwable]]
              compiler.askType(sourceFile, true, typed)
              typed.get match {
                case Left(body0) =>
                  val problems0 = if (file != null) compiler.problemsOf(file) else Nil
                  (body0, problems0)
                case Right(thr) =>
                  ScalaPlugin.plugin.logError("Failure in presentation compiler", thr)
                  (compiler.EmptyTree, Nil)
              }
            }
          }
          
          (treeHolder, true)
        }
      }
    
    // Problem reporting must be done outside of sync to avoid
    // a potential deadlock wrt buffer modification
    if (changed) {
      val problemRequestor = getProblemRequestor
      if (problemRequestor != null) {
        try {
          problemRequestor.beginReporting
          result.problems.map(problemRequestor.acceptProblem(_))
        } finally {
          problemRequestor.endReporting
        }
      }
    }
    
    result
  }
  
  override def bufferChanged(e : BufferChangedEvent) {
    if (e.getBuffer.isClosed)
      discard
    else synchronized {
      sFile = null
      treeHolder = null
    }

    super.bufferChanged(e)
  }
  
  def discard {
    synchronized {
      if (treeHolder != null) {
        val th = treeHolder
        import th._
  
        compiler.removeUnitOf(sFile)
        sFile = null
        treeHolder = null
      }
    }
  }
  
  override def close {
    discard
    super.close
  }
  
  def getSourceFile : SourceFile = {
    if (getBuffer == null)
      throw new NullPointerException("getBuffer == null for: "+getElementName)
    
    val buffer = {
      val buffer0 = getBuffer.getCharacters
      if (buffer0 != null)
        buffer0
      else {
        new Array[Char](0)
      }
    }
      
    synchronized {
      if (sFile == null)
        sFile = new BatchSourceFile(aFile, buffer) 
      sFile
    }
  }

  def getProblemRequestor : IProblemRequestor = null

  override def buildStructure(info : OpenableElementInfo, pm : IProgressMonitor, newElements : JMap[_, _], underlyingResource : IResource) : Boolean = {
    val th = getTreeHolder
    import th._

    val buffer = getBuffer
    if (body == null || body.isEmpty || buffer == null) {
      info.setIsStructureKnown(false)
      return info.isStructureKnown
    }
    
    val sourceLength = buffer.getLength
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
  
  def newSearchableEnvironment(workingCopyOwner : WorkingCopyOwner) : SearchableEnvironment = {
    val javaProject = getJavaProject.asInstanceOf[JavaProject]
    javaProject.newSearchableNameEnvironment(workingCopyOwner)
  }

  def newSearchableEnvironment() : SearchableEnvironment =
    newSearchableEnvironment(DefaultWorkingCopyOwner.PRIMARY)
  
  override def codeSelect(cu : env.ICompilationUnit, offset : Int, length : Int, workingCopyOwner : WorkingCopyOwner) : Array[IJavaElement] = {
    val environment = newSearchableEnvironment(workingCopyOwner)
    val requestor = new ScalaSelectionRequestor(environment.nameLookup, this)
    val buffer = getBuffer
    if (buffer != null) {
      val end = buffer.getLength
      if (offset < 0 || length < 0 || offset + length > end )
        throw new JavaModelException(new JavaModelStatus(IJavaModelStatusConstants.INDEX_OUT_OF_BOUNDS))
  
      val engine = new ScalaSelectionEngine(environment, requestor, getJavaProject.getOptions(true))
      engine.select(cu, offset, offset + length - 1)
    }
    
    requestor.getElements
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
    
    val engine = new ScalaCompletionEngine
    engine.complete(cu, unitToSkip, position, requestor, owner, typeRoot, monitor)
  }
  
  override def reportMatches(matchLocator : MatchLocator, possibleMatch : PossibleMatch) {
    val th = getTreeHolder 
    import th._

    if (body != null)
      new compiler.MatchLocatorTraverser(this, matchLocator, possibleMatch).traverse(body)
  }
  
  override def createOverrideIndicators(annotationMap : JMap[_, _]) {
    val th = getTreeHolder 
    import th._

    if (body != null)
      new compiler.OverrideIndicatorBuilderTraverser(this, annotationMap.asInstanceOf[JMap[AnyRef, AnyRef]]).traverse(body)
  }
  
  override def getImageDescriptor = {
    val file = getCorrespondingResource.asInstanceOf[IFile]
    if(file == null)
      null
    else {
      import ScalaImages.{ SCALA_FILE, EXCLUDED_SCALA_FILE }
      val javaProject = JavaCore.create(project.underlying)
      if(javaProject.isOnClasspath(file)) SCALA_FILE else EXCLUDED_SCALA_FILE
    }
  }
  
  override def getScalaWordFinder() : IScalaWordFinder = project.presentationCompiler
}

