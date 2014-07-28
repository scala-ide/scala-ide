package org.scalaide.util.internal.eclipse

import scala.reflect.internal.util.RangePosition
import scala.reflect.internal.util.SourceFile

import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.Region

object RegionUtils {
  implicit class RichProblem(private val problem: IProblem) extends AnyVal {
    def toRegion: IRegion =
      new Region(problem.getSourceStart(), problem.getSourceEnd() - problem.getSourceStart())
  }

  implicit class RichRegion(private val region: IRegion) extends AnyVal {
    def intersects(other: IRegion): Boolean =
      !(other.getOffset >= region.getOffset + region.getLength || other.getOffset + other.getLength - 1 < region.getOffset)

    def of(s: Array[Char]): String =
      s.slice(region.getOffset, region.getOffset + region.getLength).mkString

    def of(s: String): String =
      s.slice(region.getOffset, region.getOffset + region.getLength)

    def toRangePos(sourceFile: SourceFile): RangePosition = {
      val offset = region.getOffset()
      new RangePosition(sourceFile, offset, offset, offset + region.getLength())
    }
  }
}
