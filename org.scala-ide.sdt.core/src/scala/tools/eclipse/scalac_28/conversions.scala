package scala.tools.eclipse
package scalac_28

import scala.tools.nsc.util.Position
import scala.tools.nsc.symtab.{Symbols, Flags}
import scala.tools.nsc.util.{SourceFile, BatchSourceFile}
import scala.tools.nsc.util.RangePosition
import scala.tools.nsc.util.Chars


object conversions {
  implicit def toSingleLine(v : Position) = new Object(){
    def toSingleLine : Position = v match {
      case x : RangePosition => {
        import x._
        source match {
          case bs: BatchSourceFile if end > 0 && bs.offsetToLine(start) < bs.offsetToLine(end - 1) =>
            val pointLine = bs.offsetToLine(point)
            new RangePosition(source, bs.lineToOffset(pointLine), point, bs.lineToOffset(pointLine + 1))
          case _ => v
        }
      }
      case _ => v
    }
  }
  
  implicit def hasFlagsToString(v : Symbols#Symbol) = new Object(){
    def hasFlagsToString(mask: Long): String = {
      import v._
      Flags.flagsToString(
        flags & mask,
        if (hasAccessBoundary) privateWithin.toString else ""
      )
    }
  }
  
  implicit def identifier(v : SourceFile) = new Object(){
    def identifier(pos: Position): Option[String] = v match {
      case x : BatchSourceFile => {
        import Chars._
        import x._
        if (pos.isDefined && pos.source == this && pos.point != -1) {
          def isOK(c: Char) = isIdentifierPart(c) || isOperatorPart(c)
          Some(new String(content drop pos.point takeWhile isOK))
        } else {
          None //super.identifier(pos)
        }
      }
      case _ => None
    }
  }
  

}