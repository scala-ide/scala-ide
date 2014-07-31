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
import org.eclipse.jface.text.ITextViewer
import org.eclipse.swt.widgets.Display
import org.eclipse.ui.PlatformUI
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.core.resources.ScalaMarkers
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.ScalaWordFinder
import org.scalaide.util.internal.eclipse.EclipseUtils
import org.scalaide.util.internal.eclipse.RegionUtils
import org.scalaide.util.internal.ui.DisplayThread

object ScalaHover extends HasLogger {
  /** could return null, but prefer to return empty (see API of ITextHover). */
  private val NoHoverInfo = ""

  /**
   * Formats different error messages in a way that they look best in the editor
   * hover.
   */
  private object msgFormatter extends (String => String) {
    import HTMLPrinter._

    val UnimplementedMembers = """(class .* needs to be abstract, since:\W*it has \d+ unimplemented members\.)([\S\s]*)""".r

    override def apply(msg: String): String = msg match {
      case UnimplementedMembers(errorMsg, code) =>
        s"${convertToHTMLContent(errorMsg)}<pre>${convertToHTMLContent(code)}</pre>"
      case str =>
        convertToHTMLContent(str)
    }
  }

  final val HoverFontId = "org.scalaide.ui.font.hover"

  final val ScalaHoverStyleSheetPath = "/resources/scala-hover.css"

  /** The content of the CSS file [[ScalaHoverStyleSheetPath]]. */
  val ScalaHoverStyeSheet: String = {
    EclipseUtils.fileContentFromBundle(ScalaPlugin.plugin.pluginId, ScalaHoverStyleSheetPath) match {
      case util.Success(css) =>
        css
      case util.Failure(f) =>
        logger.warn(s"CSS file '$ScalaHoverStyleSheetPath' could not be accessed.", f)
        ""
    }
  }
}

class ScalaHover(val icu: InteractiveCompilationUnit) extends ITextHover with ITextHoverExtension with HtmlHover {
  import ScalaHover._

  override def getHoverInfo(viewer: ITextViewer, region: IRegion) = {
    icu.withSourceFile({ (src, compiler) =>
      import compiler.{stringToTermName => _, stringToTypeName => _, _}
      import RegionUtils._
      import HTMLPrinter._

      def typeInfo(t: Tree): Option[String] = askOption { () =>
        def compose(ss: List[String]): String = ss.filter(_.nonEmpty).mkString(" ")
        def defString(sym: Symbol, tpe: Type): String = {
          // NoType is returned for defining occurrences, in this case we want to display symbol info itself.
          val tpeinfo = if (tpe ne NoType) tpe.widen else sym.info
          compose(List(sym.flagString(Flags.ExplicitFlags), sym.keyString, sym.varianceString + sym.nameString +
            sym.infoString(tpeinfo)))
        }

        for (sym <- Option(t.symbol); tpe <- Option(t.tpe))
          yield if (sym.isClass || sym.isModule) sym.fullName else defString(sym, tpe)
      } getOrElse None

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
        val resp = new Response[Tree]
        askTypeAt(region.toRangePos(src), resp)

        val content = resp.get.left.toOption.flatMap(typeInfo).getOrElse("")
        if (content.isEmpty())
          NoHoverInfo
        else
          createHtmlOutput { sb =>
            sb append convertToHTMLContent(content)
          }
      }

      /**
       * The active workbench, which gives us access to markers, can only be
       * accessed on the UI thread.
       *
       * Because the hover itself does not run on the UI thread, it must be
       * ensured that the context is switched to the UI thread before this
       * method is called.
       *
       * This method throws an exception if it isn't called on the UI thread.
       */
      def retrieveMarkerMessages: Seq[String] = {
        require(Display.getCurrent() != null && Thread.currentThread() == Display.getCurrent().getThread(),
            "this method needs to be called on the UI thread")

        val w = PlatformUI.getWorkbench().getActiveWorkbenchWindow()
        val input = w.getActivePage().getActiveEditor().getEditorInput()
        val res = input.getAdapter(classOf[IResource]).asInstanceOf[IResource]
        val markerType = ScalaPlugin.plugin.problemMarkerId
        val markers = res.findMarkers(markerType, /* includeSubtypes */ false, IResource.DEPTH_ZERO)
        val intersections = markers filter { m =>
          val r = RegionUtils.regionOf(
            m.getAttribute(IMarker.CHAR_START, 0),
            m.getAttribute(IMarker.CHAR_END, 0))

          region.intersects(r)
        }
        intersections.map(_.getAttribute(ScalaMarkers.FullErrorMessage).asInstanceOf[String]).toSeq
      }

      val problems = problemsOf(src.file)
      val intersections = problems filter (p => region.intersects(p.toRegion))

      /* Delegate work to UI thread and block until result arrives */
      lazy val markerMessages = {
        var res: Seq[String] = null
        DisplayThread.syncExec {
          res = retrieveMarkerMessages
        }
        res
      }

      if (intersections.nonEmpty)
        typecheckingErrorMessage(intersections)
      else if (markerMessages.nonEmpty)
        buildErrorMessage(markerMessages)
      else
        typeMessage

    }) getOrElse NoHoverInfo
  }

  override def getHoverRegion(viewer: ITextViewer, offset: Int) = {
    ScalaWordFinder.findWord(viewer.getDocument, offset)
  }

  override def getHoverControlCreator(): IInformationControlCreator =
    new HoverControlCreator(new FocusedControlCreator(HoverFontId), HoverFontId)

}
