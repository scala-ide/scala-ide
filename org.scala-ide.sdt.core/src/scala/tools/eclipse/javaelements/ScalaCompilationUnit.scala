/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package javaelements

import scala.tools.eclipse.util.{Tracer, Defensive}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.{ Map => JMap, HashMap => JHashMap }
import org.eclipse.core.resources.{ IFile, IResource }
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.{
  BufferChangedEvent, CompletionRequestor, IBuffer, IBufferChangedListener, IJavaElement, IJavaModelStatusConstants,
  IProblemRequestor, ITypeRoot, JavaCore, JavaModelException, WorkingCopyOwner, IClassFile }
import org.eclipse.jdt.internal.compiler.env
import org.eclipse.jdt.internal.core.{
  BufferManager, CompilationUnitElementInfo, DefaultWorkingCopyOwner, JavaModelStatus, JavaProject, Openable,
  OpenableElementInfo, SearchableEnvironment }
import org.eclipse.jdt.internal.core.search.matching.{ MatchLocator, PossibleMatch }
import org.eclipse.jface.text.{IRegion, ITextSelection}
import org.eclipse.ui.texteditor.ITextEditor
import scala.tools.nsc.util.{ SourceFile }
import scala.tools.eclipse.contribution.weaving.jdt.{ IScalaCompilationUnit, IScalaWordFinder }
import scala.tools.eclipse.{ ScalaImages, ScalaPlugin, ScalaPresentationCompiler, ScalaSourceIndexer, ScalaWordFinder }
import scala.tools.eclipse.util.ReflectionUtils
import scala.tools.eclipse.util.FileUtils
import scala.tools.nsc.io.AbstractFile

trait ScalaCompilationUnit extends Openable with env.ICompilationUnit with ScalaElement with IScalaCompilationUnit with IBufferChangedListener {
  val project = ScalaPlugin.plugin.getScalaProject(getJavaProject.getProject)
  
  private var _changed = new AtomicBoolean(true)

  def file : AbstractFile = _file
  private val _file = (
    FileUtils.toAbstractFile(Option(this.getCorrespondingResource.asInstanceOf[IFile]))
    .orElse(FileUtils.toAbstractFile(getElementName, getPath.toString))
    .orNull
  )
  
  def doWithSourceFile(op : (SourceFile, ScalaPresentationCompiler) => Unit) {
    project.withSourceFile(this.file)(op)(())
  }
  
  def withSourceFile[T](op : (SourceFile, ScalaPresentationCompiler) => T)(orElse: => T = project.defaultOrElse) : T = {
    project.withSourceFile(this.file)(op)(orElse)
  }
  
  def withSourceFileButNotInMainThread[T](default : => T)(op : (SourceFile, ScalaPresentationCompiler) => T) : T = {
    (Thread.currentThread.getName == "main") match {
      case true => {
        Tracer.printlnWithStack("cancel/default call to withSourceFile in main Thread")
        default
      }
      case false => project.withSourceFile(this.file)(op)(default)
    }
  }

  override def bufferChanged(e : BufferChangedEvent) {
    _changed.set(true)
    super.bufferChanged(e)
  }
  
  def getProblemRequestor : IProblemRequestor = null

  override def buildStructure(info : OpenableElementInfo, pm : IProgressMonitor, newElements : JMap[_, _], underlyingResource : IResource) : Boolean = {
    Tracer.println("buildStructure : " + underlyingResource)
    //Can freeze UI if in main Thread
    project.withPresentationCompiler ({ compiler =>
      import scala.tools.eclipse.util.IDESettings
      if (IDESettings.compileOnTyping.value && _changed.getAndSet(false)) {
        val contents = this.getContents
        compiler.askReload(file, contents)
      }
    })()
    
    
    val v = withSourceFile({ (sourceFile, compiler) =>
      val unsafeElements = newElements.asInstanceOf[JMap[AnyRef, AnyRef]]
      val tmpMap = new JHashMap[AnyRef, AnyRef]
      val sourceLength = sourceFile.length
      compiler.withStructure(sourceFile) { tree =>
        compiler.ask { () =>
            new compiler.StructureBuilderTraverser(this, info, tmpMap, sourceLength).traverse(tree)
          }
        }
      info match {
        case cuei : CompilationUnitElementInfo => 
          cuei.setSourceLength(sourceLength)
        case _ =>
      }
      unsafeElements.putAll(tmpMap)
      true
    }) (false)
    info.setIsStructureKnown(v)
    info.isStructureKnown
  }
  
  def addToIndexer(indexer : ScalaSourceIndexer) {
    doWithSourceFile { (source, compiler) =>
      compiler.withParseTree(source) { tree =>
        new compiler.IndexBuilderTraverser(indexer).traverse(tree)
      }
    }
  }
  
  def newSearchableEnvironment(workingCopyOwner : WorkingCopyOwner) : SearchableEnvironment = {
    val javaProject = getJavaProject.asInstanceOf[JavaProject]
    javaProject.newSearchableNameEnvironment(workingCopyOwner)
  }

  def newSearchableEnvironment() : SearchableEnvironment =
    newSearchableEnvironment(DefaultWorkingCopyOwner.PRIMARY)
  
  override def getSourceElementAt(pos : Int) : IJavaElement = {
    super.getSourceElementAt(pos) match {
      case smie : ScalaModuleInstanceElement => smie.getParent
      case elem => elem 
    }
  }
    
  override def codeSelect(cu : env.ICompilationUnit, offset : Int, length : Int, workingCopyOwner : WorkingCopyOwner) : Array[IJavaElement] = {
    Array.empty
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
  }
  
  override def reportMatches(matchLocator : MatchLocator, possibleMatch : PossibleMatch) {
    doWithSourceFile { (sourceFile, compiler) =>
      compiler.withStructure(sourceFile) { tree =>
        compiler.ask { () =>
          compiler.MatchLocator(this, matchLocator, possibleMatch).traverse(tree)
        }
      }
    }
  }
  
  override def createOverrideIndicators(annotationMap : JMap[_, _]) {
    doWithSourceFile { (sourceFile, compiler) =>
      compiler.withStructure(sourceFile) { tree =>
        compiler.ask { () =>
          new compiler.OverrideIndicatorBuilderTraverser(this, annotationMap.asInstanceOf[JMap[AnyRef, AnyRef]]).traverse(tree)
        }
      }
    }
  }
  
  override def getImageDescriptor = {
    Option(getCorrespondingResource) map { file =>
      import ScalaImages.{ SCALA_FILE, EXCLUDED_SCALA_FILE }
      val javaProject = JavaCore.create(project.underlying)
      if (javaProject.isOnClasspath(file)) SCALA_FILE else EXCLUDED_SCALA_FILE
    } orNull
  }
  
  override def getScalaWordFinder() : IScalaWordFinder = ScalaWordFinder
  
  def followReference(editor : ITextEditor, selection : ITextSelection) = {
    val region = new IRegion {
      def getOffset = selection.getOffset
      def getLength = selection.getLength
    }
    new ScalaHyperlinkDetector().detectHyperlinks(editor, region, false) match {
      case Array(hyp) => hyp.open
      case _ =>  
    }
  }
}

object OpenableUtils extends ReflectionUtils {
  private val oClazz = classOf[Openable]
  private val openBufferMethod = getDeclaredMethod(oClazz, "openBuffer", classOf[IProgressMonitor], classOf[AnyRef])
  private val getBufferManagerMethod = getDeclaredMethod(oClazz, "getBufferManager")

  def openBuffer(o : Openable, pm : IProgressMonitor, info : AnyRef) : IBuffer = openBufferMethod.invoke(o, pm, info).asInstanceOf[IBuffer]
  def getBufferManager(o : Openable) : BufferManager = getBufferManagerMethod.invoke(o).asInstanceOf[BufferManager]
}

