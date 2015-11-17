package org.scalaide.ui.internal.editor.hover

import scala.tools.nsc.symtab.Flags
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IResource
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jface.internal.text.html.HTMLPrinter
import org.eclipse.jface.text.IInformationControlCreator
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextHover
import org.eclipse.jface.text.ITextHoverExtension
import org.eclipse.jface.text.ITextHoverExtension2
import org.eclipse.jface.text.ITextViewer
import org.eclipse.swt.widgets.Display
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
import org.scalaide.core.resources.ScalaMarkers
import org.scalaide.logging.HasLogger
import org.scalaide.util.ScalaWordFinder
import org.scalaide.util.eclipse.EditorUtils
import org.scalaide.util.eclipse.OSGiUtils
import org.scalaide.util.eclipse.RegionUtils
import org.scalaide.util.ui.DisplayThread
import org.scalaide.core.SdtConstants
import scala.tools.nsc.interactive.CompilerControl
import scala.tools.nsc.symtab.Flags
import org.scalaide.core.internal.compiler.ScalaPresentationCompiler
import org.eclipse.jdt.internal.ui.text.java.hover.JavadocHover
import org.scalaide.ui.editor.InteractiveCompilationUnitEditor
import org.eclipse.core.filebuffers.FileBuffers
import org.scalaide.core.extensions.SourceFileProviderRegistry
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jface.text.Region
import org.scalaide.ui.editor.hover.IScalaHover

object ScalaHoverImpl extends HasLogger {
  /** could return null, but prefer to return empty (see API of ITextHover). */
  private val NoHoverInfo = ""

  /** Formats different error messages in a way that they look best in the editor
   *  hover.
   */
  private object msgFormatter extends (String => String) with HtmlHover {

    val UnimplementedMembers = """(class .* needs to be abstract, since:\W*it has \d+ unimplemented members\.)([\S\s]*)""".r

    override def apply(msg: String): String = msg match {
      case UnimplementedMembers(errorMsg, code) =>
        s"${convertContentToHtml(errorMsg)}<pre><code>${convertContentToHtml(code)}</code></pre>"
      case str =>
        convertContentToHtml(str)
    }
  }
}

class ScalaHoverImpl extends ITextHover with ITextHoverExtension with ITextHoverExtension2 with HtmlHover {
  import ScalaHoverImpl._
  import IScalaHover._

  private var icuEditor: Option[InteractiveCompilationUnitEditor] = None

  def this(editor: InteractiveCompilationUnitEditor) {
    this()
    icuEditor = Option(editor)
  }

  /** Return a compilation unit corresponding to the given text viewer. May return `null` if not found.
   *
   *  This method relies on finding the associated document in the platform file-buffer manager. Files
   *  open in an editor get there if the document provider is an instance of `FileDocumentProvider`,
   *  but some editors don't rely on it (for example, the classfile editor).
   */
  private def bufferedUnitFor(textViewer: ITextViewer): Option[InteractiveCompilationUnit] = {
    val doc = textViewer.getDocument
    for {
      buffer <- Option(FileBuffers.getTextFileBufferManager.getTextFileBuffer(doc))
      location = buffer.getLocation
      provider <- Option(SourceFileProviderRegistry.getProvider(location))
      icu <- provider.createFrom(location)
    } yield icu
  }

  protected def getCompilationUnit(viewer: ITextViewer): Option[InteractiveCompilationUnit] = {
    icuEditor.map(_.getInteractiveCompilationUnit()).orElse(bufferedUnitFor(viewer))
  }

  /**
   * Returns the focused control creator, which is known as the presenter
   * control creator in the Eclipse API.
   *
   * This method is needed because we can directly open a focused hover,
   * without going through the unfocused variant. This happens for example when
   * an user invokes the "Show Tooltip Description" action of the editor.
   */
  def getInformationPresenterControlCreator(): IInformationControlCreator =
    new FocusedControlCreator(HoverFontId)

  /** Return the associated hover information for the given region. The region is assumed to be
   *  relative to the compilation unit, and this implementation uses the associated compilation unit
   *  to translate it to a Scala-based offset.
   *
   *  For example, if the hover is installed on a Play template editor, the region is relative to the
   *  template contents. This implementation will perform on-the-fly translation to Scala (using the
   *  compilation unit) and translate the offset to the correct Scala-based index.
   */
  override def getHoverInfo2(viewer: ITextViewer, region: IRegion): AnyRef = {
    val hoverOpt = for {
      icu <- getCompilationUnit(viewer)
    } yield {
      icu.withSourceFile({ (src, compiler) =>
        import compiler.{ stringToTermName => _, stringToTypeName => _, _ }
        import RegionUtils.RichRegion
        import RegionUtils.RichProblem
        import HTMLPrinter._

        val scalaRegion = new Region(icu.sourceMap(viewer.getDocument.get.toCharArray).scalaPos(region.getOffset), region.getLength)

        def pre(tsym: Symbol, t: Tree): Type = t match {
          case Apply(fun, _)                   => pre(tsym, fun)
          case Select(qual, _)                 => qual.tpe
          case _ if tsym.enclClass ne NoSymbol => ThisType(tsym.enclClass)
          case _                               => NoType
        }

        def typeInfo(t: Tree): Option[String] =
          (for (sym <- Option(t.symbol);
                tpe <- Option(pre(sym, t)))
            yield compiler.headerForSymbol(sym, tpe)).flatten

        def typecheckingErrorMessage(problems: Seq[IProblem]) = {
          createHtmlOutput { sb =>
            problems.map(_.getMessage()).distinct map msgFormatter match {
              case Seq(msg) =>
                sb append msg
              case msgs =>
                startBulletList(sb)
                msgs foreach (msg => addBullet(sb, msg))
                endBulletList(sb)
            }
          }
        }

        def buildErrorMessage(problems: Seq[String]) = {
          createHtmlOutput { sb =>
            problems.distinct map msgFormatter match {
              case Seq(msg) =>
                sb append msg
              case msgs =>
                startBulletList(sb)
                msgs foreach (msg => addBullet(sb, msg))
                endBulletList(sb)
            }
          }
        }

        def typeMessage = {
          val tree = askTypeAt(scalaRegion.toRangePos(src)).getOption()

          val content = tree.flatMap(typeInfo).getOrElse("")
          if (content.isEmpty())
            NoHoverInfo
          else
            createHtmlOutput { sb =>
              sb append convertToHTMLContent(content)
            }
        }

        /** The active workbench, which gives us access to markers, can only be
         *  accessed on the UI thread.
         *
         *  Because the hover itself does not run on the UI thread, it must be
         *  ensured that the context is switched to the UI thread before this
         *  method is called.
         *
         *  This method throws an exception if it isn't called on the UI thread.
         */
        def retrieveMarkerMessages: Seq[String] = {
          require(Display.getCurrent() != null && Thread.currentThread() == Display.getCurrent().getThread(),
            "this method needs to be called on the UI thread")

          EditorUtils.resourceOfActiveEditor.map { res =>
            val markerType = SdtConstants.ProblemMarkerId
            val markers = res.findMarkers(markerType, /* includeSubtypes */ false, IResource.DEPTH_ZERO)
            val markersInRange = markers filter { m =>
              val r = RegionUtils.regionOf(
                m.getAttribute(IMarker.CHAR_START, 0),
                m.getAttribute(IMarker.CHAR_END, 0))

              region.intersects(r)
            }
            markersInRange.map(_.getAttribute(ScalaMarkers.FullErrorMessage, "")).toSeq
          }.getOrElse(Seq())
        }

        val problems = problemsOf(icu)
        val problemsInRange = problems filter (p => region.intersects(p.toRegion))

        /* Delegate work to UI thread and block until result arrives */
        lazy val markerMessages = {
          var res: Seq[String] = null
          DisplayThread.syncExec {
            res = retrieveMarkerMessages
          }
          res
        }

        // time consuming, so this is lazy. Might not need to be forced when there are compilation errors.
        // It's important (for performance reasons) that this code runs *after* retrieving errors. At the end
        // of a scaladoc request the presentation compiler needs to unload any temporary compilation units
        // needed during this request. That leads to a fresh run, and a full type-checking of all loaded
        // sources, so asking for errors right after will generally be slow.
        lazy val docComment = {
          val thisComment = {
            import compiler._
            val wordPos = scalaRegion.toRangePos(src)
            val pos = { val pTree = locateTree(wordPos); if (pTree.hasSymbolField) pTree.pos else wordPos }
            val tree = askTypeAt(pos).getOption()
            val askedOpt = asyncExec {

              for (
                t <- tree;
                tsym <- Option(t.symbol);
                pt <- Option(pre(tsym, t))
              ) yield {
                val site = pt.typeSymbol
                val sym = if (tsym.isCaseApplyOrUnapply) site else tsym
                val header = headerForSymbol(sym, pt)
                (sym, site, header)
              }
            }.getOption().flatten

            for {
              (sym, site, header) <- askedOpt
              comment <- parsedDocComment(sym, site, icu.scalaProject.javaProject)
            } yield
              (new ScalaDocHtmlProducer).getBrowserInput(compiler)(comment, sym, header.getOrElse(""))
          }
          thisComment.flatten
        }

        if (problemsInRange.nonEmpty)
          typecheckingErrorMessage(problemsInRange)
        else if (markerMessages.nonEmpty)
          buildErrorMessage(markerMessages)
        else if (docComment.isDefined)
          docComment.get
        else
          typeMessage

      }) getOrElse NoHoverInfo
    }

    hoverOpt getOrElse NoHoverInfo
  }

  @deprecated("Use getHoverInfo2", "4.0.0")
  override def getHoverInfo(viewer: ITextViewer, region: IRegion) = null

  override def getHoverRegion(viewer: ITextViewer, offset: Int) = {
    ScalaWordFinder.findWord(viewer.getDocument, offset)
  }

  override def getHoverControlCreator(): IInformationControlCreator =
    new HoverControlCreator(getInformationPresenterControlCreator(), HoverFontId)

}
