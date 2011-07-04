/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.jdt.core.ICodeAssist
import org.eclipse.jface.text.{ ITextViewer, IRegion, ITextHover }

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.nsc.symtab.Flags

class ScalaHover(codeAssist : () => Option[ICodeAssist]) extends ITextHover {
  
  private val NoHoverInfo = "" // could return null, but prefer to return empty (see API of ITextHover).
    
  override def getHoverInfo(viewer : ITextViewer, region :  IRegion) = {
    codeAssist() match {
      case Some(scu : ScalaCompilationUnit) => {
        val start = region.getOffset
        val end = start + region.getLength
        scu.withSourceFile ({ (src, compiler) =>
          import compiler._
          
          def hoverInfo(t: Tree): Option[String] = askOption { () =>
            def compose(ss: List[String]): String = ss.filter("" !=).mkString("", " ", "")
            def defString(sym: Symbol, tpe: Type): String = {
              // NoType is returned for defining occurrences, in this case we want to display symbol info itself.
              val tpeinfo = if (tpe ne NoType) tpe.widen else sym.info
              compose(List(sym.hasFlagsToString(Flags.ExplicitFlags), sym.keyString, sym.varianceString + sym.nameString + 
              sym.infoString(tpeinfo)))
            }
            
            for (sym <- Option(t.symbol); tpe <- Option(t.tpe))
              yield if (sym.isClass || sym.isModule) sym.fullName else defString(sym, tpe)
          } getOrElse None


          
          val resp = new Response[Tree]
          val range = compiler.rangePos(src, start, start, end)
          askTypeAt(range, resp)
          (for (t <- resp.get.left.toOption;
              hover <- hoverInfo(t)) yield hover) getOrElse NoHoverInfo
        }) (NoHoverInfo)
      }
      case _ => NoHoverInfo
    }
  }
  
  override def getHoverRegion(viewer : ITextViewer, offset : Int) = {
    ScalaWordFinder.findWord(viewer.getDocument, offset)
  }
}
