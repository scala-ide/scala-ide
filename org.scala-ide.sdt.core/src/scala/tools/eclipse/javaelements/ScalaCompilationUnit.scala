/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import java.util.{ Map => JMap }

import scala.concurrent.SyncVar

import org.eclipse.core.internal.filebuffers.SynchronizableDocument
import org.eclipse.core.resources.{ IFile, IResource }
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.{
  BufferChangedEvent, CompletionRequestor, IBuffer, IBufferChangedListener, IJavaElement, IJavaModelStatusConstants,
  IProblemRequestor, ITypeRoot, JavaCore, JavaModelException, WorkingCopyOwner }
import org.eclipse.jdt.internal.compiler.env
import org.eclipse.jdt.internal.core.{
  BufferManager, CompilationUnitElementInfo, DefaultWorkingCopyOwner, JavaModelStatus, JavaProject, Openable,
  OpenableElementInfo, SearchableEnvironment }
import org.eclipse.jdt.internal.core.search.matching.{ MatchLocator, PossibleMatch }
import org.eclipse.jdt.internal.ui.javaeditor.DocumentAdapter

import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.{ BatchSourceFile, SourceFile }

import scala.tools.eclipse.contribution.weaving.jdt.{ IScalaCompilationUnit, IScalaWordFinder }

import scala.tools.eclipse.{ ScalaImages, ScalaPlugin, ScalaPresentationCompiler, ScalaSourceIndexer, ScalaWordFinder }
import scala.tools.eclipse.util.{ Cached, ReflectionUtils }

trait ScalaCompilationUnit extends Openable with env.ICompilationUnit with ScalaElement with IScalaCompilationUnit with IBufferChangedListener {
  val project = ScalaPlugin.plugin.getScalaProject(getJavaProject.getProject)
  val cachedSourceFile = new Cached[SourceFile] {
    def create = {
      val buffer = openBuffer0(null, null)
      if (buffer == null)
        throw new NullPointerException("getBuffer == null for: "+getElementName)
      
      val contents = {
        val contents0 = buffer.getCharacters
        if (contents0 != null)
          contents0
        else
          new Array[Char](0)
      }
      
      new BatchSourceFile(file, contents)
    }
    
    def destroy(sf : SourceFile) {}
  }

  def file : AbstractFile
  
	def withCompilerResult[T](op : ScalaPresentationCompiler.CompilerResultHolder => T) : T = {
    project.withCompilerResult(this, cachedSourceFile(identity))(op)
  }
  
  override def bufferChanged(e : BufferChangedEvent) {
    if (e.getBuffer.isClosed)
      discard
    else {
      cachedSourceFile.invalidate
      project.withPresentationCompiler(_.invalidateCompilerResult(this))
    }
    
    super.bufferChanged(e)
  }
  
  def discard {
    cachedSourceFile.invalidate
    project.withPresentationCompiler(_.discardCompilerResult(this))
  }
  
  override def close {
    discard
    super.close
  }

  private def openBuffer0(pm : IProgressMonitor, info : AnyRef) = OpenableUtils.openBuffer(this, pm, info)

  def getProblemRequestor : IProblemRequestor = null

  override def buildStructure(info : OpenableElementInfo, pm : IProgressMonitor, newElements : JMap[_, _], underlyingResource : IResource) : Boolean =
  	withCompilerResult({ crh =>
			import crh._
	
	    if (body == null || body.isEmpty) {
	      info.setIsStructureKnown(false)
	      return info.isStructureKnown
	    }
	    
	    val sourceLength = sourceFile.length
	    new compiler.StructureBuilderTraverser(this, info, newElements.asInstanceOf[JMap[AnyRef, AnyRef]], sourceLength).traverse(body)
	    
	    info match {
	      case cuei : CompilationUnitElementInfo =>
	        cuei.setSourceLength(sourceLength)
	      case _ =>
	    }
	
	    info.setIsStructureKnown(true)
	    info.isStructureKnown
  })

  def scheduleReconcile : Unit = ()
  
  def addToIndexer(indexer : ScalaSourceIndexer) {
    withCompilerResult({ crh =>
	    import crh._
	
	    if (body != null)
	      new compiler.IndexBuilderTraverser(indexer).traverse(body)
	  })
  }
  
  def newSearchableEnvironment(workingCopyOwner : WorkingCopyOwner) : SearchableEnvironment = {
    val javaProject = getJavaProject.asInstanceOf[JavaProject]
    javaProject.newSearchableNameEnvironment(workingCopyOwner)
  }

  def newSearchableEnvironment() : SearchableEnvironment =
    newSearchableEnvironment(DefaultWorkingCopyOwner.PRIMARY)
  
  override def getSourceElementAt(pos : Int) : IJavaElement = {
    super.getSourceElementAt(pos) match {
      case smie : ScalaModuleInstanceElement => smie.getParent;
      case elem => elem 
    }
  }
    
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
    withCompilerResult({ crh =>
	    import crh._
	
	    if (body != null)
	      new compiler.MatchLocatorTraverser(this, matchLocator, possibleMatch).traverse(body)
	  })
  }
  
  override def createOverrideIndicators(annotationMap : JMap[_, _]) {
    withCompilerResult({ crh =>
	    import crh._
	
	    if (body != null)
	      new compiler.OverrideIndicatorBuilderTraverser(this, annotationMap.asInstanceOf[JMap[AnyRef, AnyRef]]).traverse(body)
	  })
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
  
  override def getScalaWordFinder() : IScalaWordFinder = ScalaWordFinder
}

object OpenableUtils extends ReflectionUtils {
  private val oClazz = classOf[Openable]
  private val openBufferMethod = getDeclaredMethod(oClazz, "openBuffer", classOf[IProgressMonitor], classOf[AnyRef])
  private val getBufferManagerMethod = getDeclaredMethod(oClazz, "getBufferManager")

  def openBuffer(o : Openable, pm : IProgressMonitor, info : AnyRef) : IBuffer = openBufferMethod.invoke(o, pm, info).asInstanceOf[IBuffer]
  def getBufferManager(o : Openable) : BufferManager = getBufferManagerMethod.invoke(o).asInstanceOf[BufferManager]
}
