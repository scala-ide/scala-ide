package org.scalaide.ui.internal.editor.hover

import scala.tools.nsc.symtab.Flags

import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jface.internal.text.html.HTMLPrinter
import org.eclipse.jface.text.IInformationControlCreator
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextHover
import org.eclipse.jface.text.ITextHoverExtension
import org.eclipse.jface.text.ITextViewer
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.ScalaWordFinder
import org.scalaide.util.internal.eclipse.EclipseUtils
import org.scalaide.util.internal.eclipse.RegionUtils

object ScalaHover extends HasLogger {
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

  private val NoHoverInfo = "" // could return null, but prefer to return empty (see API of ITextHover).

  override def getHoverInfo(viewer: ITextViewer, region: IRegion) = {
    icu.withSourceFile({ (src, compiler) =>
      import compiler.{stringToTermName => _, stringToTypeName => _, _}

      def hoverInfo(t: Tree): Option[String] = askOption { () =>
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

      import RegionUtils._
      import HTMLPrinter._

      def problemMessage(problems: Seq[IProblem]) = {
        createHtmlOutput { sb =>
          problems.map(_.getMessage()).distinct match {
            case Seq(msg) =>
              sb append convertToHTMLContent(msg)
            case msgs =>
              startBulletList(sb)
              msgs foreach (msg => addBullet(sb, convertToHTMLContent(msg)))
              endBulletList(sb)
          }
        }
      }

      def typeMessage = {
        val resp = new Response[Tree]
        askTypeAt(region.toRangePos(src), resp)

        val content = resp.get.left.toOption.flatMap(hoverInfo).getOrElse("")
        if (content.isEmpty())
          NoHoverInfo
        else
          createHtmlOutput { sb =>
            sb append convertToHTMLContent(content)
          }
      }

      val problems = problemsOf(src.file)
      val intersections = problems filter (p => region.intersects(p.toRegion))

      if (intersections.nonEmpty)
        problemMessage(intersections)
      else
        typeMessage

    }) getOrElse (NoHoverInfo)
  }

  override def getHoverRegion(viewer: ITextViewer, offset: Int) = {
    ScalaWordFinder.findWord(viewer.getDocument, offset)
  }

  override def getHoverControlCreator(): IInformationControlCreator =
    new HoverControlCreator(new FocusedControlCreator(HoverFontId), HoverFontId)

}
