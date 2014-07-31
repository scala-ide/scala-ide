package org.scalaide.ui.internal.editor

import org.eclipse.jdt.core.ICodeAssist
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextHover
import scala.tools.nsc.symtab.Flags
import org.scalaide.util.internal.ScalaWordFinder
import org.scalaide.util.internal.eclipse.EclipseUtils._
import org.scalaide.core.compiler.InteractiveCompilationUnit

class ScalaHover(val icu: InteractiveCompilationUnit) extends ITextHover {

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

      val resp = new Response[Tree]
      askTypeAt(region.toRangePos(src), resp)
      (for (
        t <- resp.get.left.toOption;
        hover <- hoverInfo(t)
      ) yield hover) getOrElse NoHoverInfo
    }) getOrElse (NoHoverInfo)
  }

  override def getHoverRegion(viewer: ITextViewer, offset: Int) = {
    ScalaWordFinder.findWord(viewer.getDocument, offset)
  }
}
