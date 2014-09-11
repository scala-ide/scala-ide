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
import org.scalaide.util.internal.ScalaWordFinder
import org.scalaide.util.internal.eclipse.EditorUtils
import org.scalaide.util.internal.eclipse.OSGiUtils
import org.scalaide.util.internal.eclipse.RegionUtils
import org.scalaide.util.internal.ui.DisplayThread
import org.scalaide.core.SdtConstants

object ScalaHover extends HasLogger {
  /** could return null, but prefer to return empty (see API of ITextHover). */
  private val NoHoverInfo = ""

  /**
   * Formats different error messages in a way that they look best in the editor
   * hover.
   */
  private object msgFormatter extends (String => String) with HtmlHover {
    import HTMLPrinter._

    val UnimplementedMembers = """(class .* needs to be abstract, since:\W*it has \d+ unimplemented members\.)([\S\s]*)""".r

    override def apply(msg: String): String = msg match {
      case UnimplementedMembers(errorMsg, code) =>
        s"${convertContentToHtml(errorMsg)}<pre><code>${convertContentToHtml(code)}</code></pre>"
      case str =>
        convertContentToHtml(str)
    }
  }

  /**
   * The Id that is used as a key for the preference store to retrieve the
   * configured font style to be used by the hover.
   */
  final val HoverFontId = "org.scalaide.ui.font.hover"

  /**
   * The Id that is used as a key for the preference store to retrieve the
   * stored CSS file.
   */
  final val ScalaHoverStyleSheetId = "org.scalaide.ui.config.scalaHoverCss"

  /**
   * This Id is used as a key for the preference store to retrieve the content
   * of the default CSS file. This file is already stored in the IDE bundle
   * and can be found with [[ScalaHoverStyleSheetPath]] but it is nonetheless
   * necessary to store this file because it may change in a newer version of
   * the IDE. To detect such a change we need to be able to compare the content
   * of the CSS file.
   */
  final val DefaultScalaHoverStyleSheetId = "org.scalaide.ui.config.defaultScalaHoverCss"

  /** The path to the default CSS file */
  final val ScalaHoverStyleSheetPath = "/resources/scala-hover.css"

  /** The content of the CSS file [[ScalaHoverStyleSheetPath]]. */
  def ScalaHoverStyleSheet: String =
    IScalaPlugin().getPreferenceStore().getString(ScalaHoverStyleSheetId)

  /** The content of the CSS file [[ScalaHoverStyleSheetPath]]. */
  def DefaultScalaHoverStyleSheet: String = {
    OSGiUtils.fileContentFromBundle(SdtConstants.PluginId, ScalaHoverStyleSheetPath) match {
      case util.Success(css) =>
        css
      case util.Failure(f) =>
        logger.warn(s"CSS file '$ScalaHoverStyleSheetPath' could not be accessed.", f)
        ""
    }
  }
}

class ScalaHover(val icu: InteractiveCompilationUnit) extends ITextHover with ITextHoverExtension with ITextHoverExtension2 with HtmlHover {
  import ScalaHover._

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

  override def getHoverInfo2(viewer: ITextViewer, region: IRegion): AnyRef =
    getHoverInfo(viewer, region)

  override def getHoverInfo(viewer: ITextViewer, region: IRegion) = {
    icu.withSourceFile({ (src, compiler) =>
      import compiler.{stringToTermName => _, stringToTypeName => _, _}
      import RegionUtils._
      import HTMLPrinter._

      def typeInfo(t: Tree): Option[String] = (for (sym <- Option(t.symbol); tpe <- Option(t.tpe)) yield compiler.headerForSymbol(sym,tpe)).flatten

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
        val tree = askTypeAt(region.toRangePos(src)).getOption()

        val content = tree.flatMap(typeInfo).getOrElse("")
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

      if (problemsInRange.nonEmpty)
        typecheckingErrorMessage(problemsInRange)
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
    new HoverControlCreator(getInformationPresenterControlCreator(), HoverFontId)

}
