/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package javaelements

import java.util.{ Map => JMap }
import scala.concurrent.SyncVar
import org.eclipse.core.internal.filebuffers.SynchronizableDocument
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
import org.eclipse.jdt.internal.ui.javaeditor.DocumentAdapter
import org.eclipse.jface.text.{IRegion, ITextSelection}
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.{ BatchSourceFile, SourceFile }
import scala.tools.eclipse.contribution.weaving.jdt.{ IScalaCompilationUnit, IScalaWordFinder }
import scala.tools.eclipse.{ ScalaImages, ScalaPlugin, ScalaPresentationCompiler, ScalaSourceIndexer, ScalaWordFinder }
import scala.tools.eclipse.util.ReflectionUtils
import org.eclipse.jdt.core._
import org.eclipse.jdt.internal.core.JavaElement
import org.eclipse.jdt.internal.core.SourceRefElement
import scala.tools.eclipse.logging.HasLogger
import scala.tools.nsc.interactive.Response
import org.eclipse.jface.text.source.ISourceViewer
import scala.tools.eclipse.hyperlink.text.detector.DeclarationHyperlinkDetector
import org.eclipse.ui.texteditor.ITextEditor
import scala.tools.eclipse.hyperlink.text.detector.BaseHyperlinkDetector
import scala.tools.eclipse.util.EditorUtils

trait ScalaCompilationUnit extends Openable
  with env.ICompilationUnit
  with ScalaElement
  with IScalaCompilationUnit
  with IBufferChangedListener
  with InteractiveCompilationUnit
  with HasLogger {

  override def scalaProject = ScalaPlugin.plugin.getScalaProject(getJavaProject.getProject)

  val file : AbstractFile

  override def sourceFile(contents: Array[Char]): SourceFile = new BatchSourceFile(file, contents)

  override def workspaceFile: IFile = getUnderlyingResource.asInstanceOf[IFile]

  override def bufferChanged(e : BufferChangedEvent) {
    if (!e.getBuffer.isClosed)
      scalaProject.doWithPresentationCompiler(_.askReload(this, getContents))

    super.bufferChanged(e)
  }

  /** Ensure the underlying buffer is open. Otherwise, `bufferChanged` events won't be fired,
   *  meaning `askReload` won't be called, and presentation compiler errors won't be reported.
   *
   *  This code is copied from org.eclipse.jdt.internal.core.CompilationUnit
   */
  private def ensureBufferOpen(info: OpenableElementInfo, pm: IProgressMonitor) {
    // ensure buffer is opened
    val buffer = super.getBufferManager().getBuffer(this);
    if (buffer == null) {
      super.openBuffer(pm, info); // open buffer independently from the info, since we are building the info
    }
  }

  override def buildStructure(info: OpenableElementInfo, pm: IProgressMonitor, newElements: JMap[_, _], underlyingResource: IResource): Boolean = {
    ensureBufferOpen(info, pm)

    withSourceFile({ (sourceFile, compiler) =>
      val unsafeElements = newElements.asInstanceOf[JMap[AnyRef, AnyRef]]
      val tmpMap = new java.util.HashMap[AnyRef, AnyRef]
      val sourceLength = sourceFile.length

      try {
        logger.info("[%s] buildStructure for %s (%s)".format(scalaProject.underlying.getName(), this.getResource(), sourceFile.file))

        compiler.withStructure(sourceFile) { tree =>
          compiler.askOption { () =>
            new compiler.StructureBuilderTraverser(this, info, tmpMap, sourceLength).traverse(tree)
          }
        }
        info match {
          case cuei: CompilationUnitElementInfo =>
            cuei.setSourceLength(sourceLength)
          case _ =>
        }

        unsafeElements.putAll(tmpMap)
        true
      } catch {
        case e: InterruptedException =>
          Thread.currentThread().interrupt()
          logger.info("ignored InterruptedException in build structure")
          false

        case ex =>
          logger.error("Compiler crash while building structure for %s".format(file), ex)
          false
      }
    })(false)
  }


  /** Schedule this unit for reconciliation. This implementation does nothing, subclasses
   *  implement custom behavior. @see ScalaSourceFile
   *
   *  @return A response on which clients can synchronize. The response
   *          only notifies when the unit was added to the managed sources list, *not*
   *          that it was typechecked.
   */
  override def scheduleReconcile(): Response[Unit] = {
    val r = (new Response[Unit])
    r.set()
    r
  }

  /** Index this source file, but only if the project has the Scala nature.
   *
   *  This avoids crashes if the indexer kicks in on a project that has Scala sources
   *  but no Scala library on the classpath.
   */
  def addToIndexer(indexer : ScalaSourceIndexer) {
    if (scalaProject.hasScalaNature) {
      try doWithSourceFile { (source, compiler) =>
        compiler.withParseTree(source) { tree =>
          new compiler.IndexBuilderTraverser(indexer).traverse(tree)
        }
      } catch {
        case ex: Throwable => logger.error("Compiler crash during indexing of %s".format(getResource()), ex)
      }
    }
  }

  override def getSourceElementAt(pos : Int) : IJavaElement = {
    getChildAt(this, pos) match {
      case smie : ScalaModuleInstanceElement => smie.getParent;
      case null => this
      case elem => elem
    }
  }

  private def getChildAt(element: IJavaElement, pos: Int): IJavaElement = {
    /* companion-class can be selected instead of the object in the JDT-'super'
       implementation and make the private method and fields unreachable.
       To avoid this, we look for deepest element containing the position
     */

    def depth(e: IJavaElement): Int = if (e == element || e == null) 0 else (depth(e.getParent()) + 1)

    element match {
      case parent: IParent => {
        var resultElement= element
        // look through the list of children from the end, because the constructor (at
        // the beginning) covers the whole source code
        for (child <- parent.getChildren().reverse) {
          child match {
            case sourceReference: ISourceReference => {
              // check if the range of the child contains the position
              val range= sourceReference.getSourceRange
              val rangeStart= range.getOffset
              if (rangeStart <= pos && pos <= rangeStart + range.getLength) {
                // look in the child itself
                val foundChild = getChildAt(child, pos)
                // check if the found element is more precise than the one previously found
                if (depth(foundChild) > depth(resultElement))
                  resultElement = foundChild
              }
            }
          }
        }
        resultElement
      }
      case elem => elem
    }
  }

  override def codeSelect(cu: env.ICompilationUnit, offset: Int, length: Int, workingCopyOwner: WorkingCopyOwner): Array[IJavaElement] = {
    withSourceFile { (srcFile, compiler) =>
      val pos = compiler.rangePos(srcFile, offset, offset, offset)

      val typed = new compiler.Response[compiler.Tree]
      compiler.askTypeAt(pos, typed)
      val typedRes = typed.get
      val element = for {
       t <- typedRes.left.toOption
       if t.hasSymbol
       sym = if (t.symbol.isSetter) t.symbol.getter(t.symbol.owner) else t.symbol
       element <- compiler.getJavaElement(sym)
      } yield Array(element: IJavaElement)

      val res = element.getOrElse(Array.empty[IJavaElement])
      res
    }(Array.empty[IJavaElement])
  }

  override def codeComplete(cu : env.ICompilationUnit, unitToSkip : env.ICompilationUnit, position : Int,
                            requestor : CompletionRequestor, owner : WorkingCopyOwner, typeRoot : ITypeRoot, monitor : IProgressMonitor) {
    // This is a no-op. The Scala IDE provides code completions via an extension point
  }

  override def reportMatches(matchLocator : MatchLocator, possibleMatch : PossibleMatch) {
    doWithSourceFile { (sourceFile, compiler) =>
      val response = new Response[compiler.Tree]
      compiler.askLoadedTyped(sourceFile, response)
      response.get match {
        case Left(tree) =>
          compiler.askOption { () =>
            compiler.MatchLocator(this, matchLocator, possibleMatch).traverse(tree)
          }
        case _ => () // no op
      }

    }
  }

  override def createOverrideIndicators(annotationMap : JMap[_, _]) {
    if (scalaProject.hasScalaNature)
      doWithSourceFile { (sourceFile, compiler) =>
        try {
          compiler.withStructure(sourceFile, keepLoaded = true) { tree =>
            compiler.askOption { () =>
              new compiler.OverrideIndicatorBuilderTraverser(this, annotationMap.asInstanceOf[JMap[AnyRef, AnyRef]]).traverse(tree)
            }
          }
        } catch {
          case ex =>
           logger.error("Exception thrown while creating override indicators for %s".format(sourceFile), ex)
        }
      }
  }

  override def getImageDescriptor = {
    Option(getCorrespondingResource) map { file =>
      import ScalaImages.{ SCALA_FILE, EXCLUDED_SCALA_FILE }
      val javaProject = JavaCore.create(scalaProject.underlying)
      if (javaProject.isOnClasspath(file)) SCALA_FILE else EXCLUDED_SCALA_FILE
    } orNull
  }

  override def getScalaWordFinder() : IScalaWordFinder = ScalaWordFinder

  def followDeclaration(editor : ITextEditor, selection : ITextSelection): Unit =
    followReference(DeclarationHyperlinkDetector(), editor, selection)

  def followReference(detectionStrategy: BaseHyperlinkDetector, editor : ITextEditor, selection : ITextSelection): Unit = {
    val region = EditorUtils.textSelection2region(selection)

    Option(detectionStrategy.detectHyperlinks(editor, region, canShowMultipleHyperlinks = false)) match {
      case Some(Array(first, _*)) => first.open	
      case _ => ()
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
