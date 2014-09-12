package org.scalaide.util.internal.eclipse

import scala.reflect.internal.util.RangePosition
import scala.reflect.internal.util.SourceFile
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.TypedRegion

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

  def regionOf(start: Int, end: Int): IRegion =
    new Region(start, end - start)
  implicit class RichTypedRegion(val region: TypedRegion) extends AnyVal {

    def shift(n: Int): TypedRegion =
        new TypedRegion(region.getOffset() + n, region.getLength(), region.getType())

    /** Checks if the given position is contained in this region.
     *  This check is inclusive. If this region has offset 5, and length 3, it will return
     *  true for 5, 6, 7 and 8.
     */
    def containsPositionInclusive(offset: Int): Boolean = {
      if (region.getLength() == 0) {
        region.getOffset() == offset
      } else {
        region.getOffset() <= offset && (region.getOffset() + region.getLength()) >= offset
      }
    }

    /** Checks if the given position is contained in this region.
     *  This check is exclusive. If this region has offset 5, and length 3, it will return
     *  true for 5, 6 and 7.
     */
    def containsPositionExclusive(offset: Int) : Boolean = {
        region.getOffset() <= offset &&  offset < (region.getOffset() + region.getLength())
    }

    def overlapsWith(otherRegion: IRegion): Boolean = {
      region.getOffset() < otherRegion.getOffset + otherRegion.getLength && otherRegion.getOffset < region.getOffset() + region.getLength
    }

    /** Check if the given region is contained in this region.
     */
    def containsRegion(innerRegion: IRegion): Boolean = {
      containsPositionInclusive(innerRegion.getOffset()) && containsPositionInclusive(innerRegion.getOffset() + innerRegion.getLength())
    }
  }

  implicit class AdvancedTypedRegionList(val a: List[TypedRegion]) extends AnyVal {
    def U(b: List[TypedRegion]) =
      union(a, b)
    def \(b: List[TypedRegion]) =
      subtract(a, b)
    def ^(b: List[TypedRegion]) =
      intersect(a, b)

  }

  /**
   * Intersects between two lists of regions
   */
  def intersect(a: List[TypedRegion], b: List[TypedRegion]): List[TypedRegion] = {
    subtract(a, subtract(a, b))
  }

  /**
   * Subtracts a list of regions from another one
   */
  def subtract(a: List[TypedRegion], b: List[TypedRegion]): List[TypedRegion] = {
    (a, b) match {
      case (x :: xs, y :: ys) =>
        val xStart = x.getOffset()
        val xEnd = xStart + x.getLength() - 1
        val yStart = y.getOffset()
        val yEnd = yStart + y.getLength() - 1
        if (x.getLength() == 0){
          subtract(xs, b)
        } else if (xEnd < yStart)
          //x: ___
          //y:      +++
          x :: subtract(xs, b)
        else if (yEnd < xStart)
          //x:      ___
          //y: +++
          subtract(a, ys)
        else if (x.containsRegion(y)) { // x contains y
          //x:   -------
          //y:    +++++
          val newElem =
            if (xStart == yStart)
              //x:  -------
              //y:  ++
              Nil
            else
              //x:  -------
              //y:    ++
              List(new TypedRegion(xStart, yStart - xStart, x.getType()))
          val producedElem =
            if (xEnd == yEnd)
              //x:  -------
              //y:       ++
              Nil
            else
              //x:  -------
              //y:      ++
              List(new TypedRegion(yEnd + 1, xEnd - yEnd, x.getType()))
          newElem ::: subtract(producedElem ::: xs, ys)
        } else if (y.containsRegion(x)) { // y contains x
          //x:    -----
          //y:   +++++++
          subtract(xs, b)
        } else if (x.containsPositionExclusive(yEnd)) {
          //x:  -------
          //y: ++++
          val producedElem = new TypedRegion(yEnd + 1, xEnd - yEnd, x.getType())
          subtract(producedElem :: xs, ys)
        } else if (x.containsPositionExclusive(yStart)) {
          //x:  -------
          //y:      ++++++
          val newElem = new TypedRegion(xStart, yStart - xStart, x.getType())
          newElem :: subtract(xs, b)
        } else {
          throw new RuntimeException("Unhandled case! Impossible!")
        }
      case (xl, Nil) => xl
      case (Nil, _) => Nil
    }
  }

  /**
   * Unions two lists of regions
   * The lists must have no intersection
   */
  def union(a: List[TypedRegion], b: List[TypedRegion]): List[TypedRegion] = {
    merge[TypedRegion](a, b, ((x, y) => x.getOffset() < y.getOffset()))
  }

  private def merge[T](aList: List[T], bList: List[T], lt: (T, T) => Boolean): List[T] = bList match {
    case Nil => aList
    case _ =>
      aList match {
        case Nil => bList
        case x :: xs =>
          if (lt(x, bList.head))
            x :: merge(xs, bList, lt)
          else
            bList.head :: merge(aList, bList.tail, lt)
      }
  }
}

