/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.jdt.core.ICodeAssist
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextHover
import org.eclipse.jface.text.ITextHoverExtension
import org.eclipse.jface.text.ITextHoverExtension2
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Shell
import org.eclipse.jface.text.IInformationControlCreator
import org.eclipse.jface.text.DefaultInformationControl

import scala.tools.nsc.interactive.CompilerControl
import scala.tools.nsc.symtab.Flags
import scala.tools.eclipse.util.EclipseUtils._

class ScalaHover(val icu: InteractiveCompilationUnit) extends ITextHover with ITextHoverExtension with ITextHoverExtension2 {

  private val NoHoverInfo = "" // could return null, but prefer to return empty (see API of ITextHover).

  @deprecated("Use getHoverInfo2","4.0.0")
  override def getHoverInfo(viewer: ITextViewer, region: IRegion) : String = null

  override def getHoverInfo2(viewer: ITextViewer, region: IRegion): Object =
    icu.withSourceFile({ (src, compiler) =>
      import compiler.{stringToTermName => _, stringToTypeName => _, _}

      def hoverInfo(t: Tree): Option[Object] = {
        val askedOpt = askOption { () =>
          def compose(ss: List[String]): String = ss.filterNot(_.isEmpty).mkString(" ")
          def defString(sym: Symbol, tpe: Type): String = {
            compose(List(sym.flagString(Flags.ExplicitFlags), sym.keyString, sym.varianceString + sym.nameString +
              sym.infoString(tpe)))
          }

          for (tsym <- Option(t.symbol)) yield {
            def pre(t: Tree): Type = t match {
              case Apply(fun, _) => pre(fun)
              case Select(qual, _) => qual.tpe
              case _ if tsym.enclClass ne NoSymbol => ThisType(tsym.enclClass)
              case _ => NoType
            }
            val pt = pre(t)
            val site = pt.typeSymbol
            val sym = if(tsym.isCaseApplyOrUnapply) site else tsym
            val header = if (sym.isClass || sym.isModule) sym.nameString else {
              val tpe = sym.tpe.asSeenFrom(pt.widen, site)
              defString(sym, tpe)
            }
            (sym, site, header)
          }
        }.flatten

        for ((sym, site, header) <- askedOpt) yield
          compiler.askOption{ () => browserInput(sym, site, header) }.getOrElse(None).getOrElse {
            val html = "<html><body><b>" + header + "</b></body></html>"
            new BrowserInput(html, sym)
          }
      }

      val wordPos = region.toRangePos(src)
      val pos = {val pTree = locateTree(wordPos); if (pTree.hasSymbol) pTree.pos else wordPos}
      val resp = new Response[Tree]
      askTypeAt(pos, resp)
      resp.get.left.toOption flatMap hoverInfo getOrElse NoHoverInfo
    }) getOrElse (NoHoverInfo)

  override def getHoverRegion(viewer: ITextViewer, offset: Int) = {
    ScalaWordFinder.findWord(viewer.getDocument, offset)
  }

  override def getHoverControlCreator() = new IInformationControlCreator {
    def createInformationControl(shell: Shell) = new DefaultInformationControl(shell, false)
  }
}
