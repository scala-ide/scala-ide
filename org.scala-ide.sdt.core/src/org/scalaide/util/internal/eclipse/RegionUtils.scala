package org.scalaide.util.internal.eclipse

import scala.reflect.internal.util.RangePosition
import scala.reflect.internal.util.SourceFile
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.text.Region
import org.eclipse.text.edits.TextEdit
import org.eclipse.jface.text.TypedRegion
import scala.annotation.tailrec

object RegionUtils {
  implicit class RichProblem(private val problem: IProblem) extends AnyVal {
    def toRegion: IRegion =
      new Region(problem.getSourceStart(), problem.getSourceEnd() - problem.getSourceStart())

    def length: Int = problem.getSourceEnd() - problem.getSourceStart()
    def start: Int = problem.getSourceStart()
    def end: Int = problem.getSourceEnd()
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

    def length: Int = region.getLength()
    def start: Int = region.getOffset()
    def end: Int = start+length
  }

  def regionOf(start: Int, end: Int): IRegion =
    new Region(start, end - start)

  implicit class RichSelection(private val sel: ITextSelection) extends AnyVal {
    def length: Int = sel.getLength()
    def start: Int = sel.getOffset()
    def end: Int = start+length
  }

  implicit class RichTextEdit(private val edit: TextEdit) extends AnyVal {
    def length: Int = edit.getLength()
    def start: Int = edit.getOffset()
    def end: Int = start+length
  }

  implicit class RichDocument(private val doc: IDocument) extends AnyVal {
    def length: Int = doc.getLength()
  }

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

    /** Merges two lists of regions.
     *
     *  @see RegionUtils.union
     */
    def unionWith(b: List[TypedRegion]): List[TypedRegion] =
      RegionUtils.union(a, b)

    /** Subtracts a list of regions from another one.
     *
     *  @see RegionUtils.subtract
     *  @throwIllegalArgumentException if one of the list is not ordered or with non-overlapping regions
     */
    def subtract(b: List[TypedRegion]): List[TypedRegion] =
      RegionUtils.subtract(a, b)

    /** Intersects between two lists of regions
     *
     *  @see RegionUtils.union
     *  @throwIllegalArgumentException if one of the list is not ordered or with non-overlapping regions
     */
    def intersectWith(b: List[TypedRegion]): List[TypedRegion] =
      RegionUtils.intersect(a, b)
  }

  /** Intersects between two lists of regions. The returned list contains the sections of the regions of list {{a}}
   *  which are also covered by the regions of list {{b}}.
   *  The regions in each list need to be ordered and non-overlapping, an {{IllegalArgumentException}} is thrown otherwise.
   *  The regions in the returned list are ordered and non-overlapping.
   *
   *  @throw IllegalArgumentException if one of the list is not ordered or with non-overlapping regions
   */
  def intersect(a: List[TypedRegion], b: List[TypedRegion]): List[TypedRegion] = {
    checkInput(a, b)

    subtractImpl(a, subtractImpl(a, b))
  }

  /** Subtracts a list of regions from another one. The returned list contains the sections of the regions of list {{a}}
   *  which are not covered by the regions of list {{b}}.
   *
   *  The regions in each list need to be ordered and non-overlapping, an {{IllegalArgumentException}} is thrown otherwise.
   *  The regions in the returned list are ordered and non-overlapping.
   *
   *  @throw IllegalArgumentException if one of the list is not ordered or with non-overlapping regions
   */
  def subtract(a: List[TypedRegion], b: List[TypedRegion]): List[TypedRegion] = {
    checkInput(a, b)

    subtractImpl(a, b)
  }

  /** Merges two lists of regions.
   *  If the 2 input lists are ordered by their offset, the result is also ordered by
   *  the offset.
   *  The resulting list might have overlapping regions, if the input regions have overlaps.
   */
  def union(a: List[TypedRegion], b: List[TypedRegion]): List[TypedRegion] = {
    merge[TypedRegion](a, b, ((x, y) => x.getOffset() < y.getOffset()))
  }

  private def checkInput(a: List[TypedRegion], b: List[TypedRegion]) {
    if (!orderedAndNonOverlapping(a))
      throw new IllegalArgumentException("The regions of the first list are not ordered and non-ovelapping")
    if (!orderedAndNonOverlapping(b))
      throw new IllegalArgumentException("The regions of the second list are not ordered and non-ovelapping")
  }

  /** Returns {{true}} if the regions are ordered and non-overlapping, false otherwise.
   */
  private def orderedAndNonOverlapping(l: List[TypedRegion]): Boolean = {
    @tailrec
    def loop(c: TypedRegion, l: List[TypedRegion]): Boolean = {
      l match {
        case head :: tail =>
          (head.getOffset >= c.getOffset + c.getLength) && loop(head, tail)
        case Nil =>
          true
      }
    }

    l match {
      case head :: tail =>
        loop(head, tail)
      case Nil =>
        true
    }
  }

  /** Implements the subtract method
   *  Lists must be ordered and non-overlapping
   */
  private def subtractImpl(a: List[TypedRegion], b: List[TypedRegion]): List[TypedRegion] = {
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

  /** Implements the merge/union method
   */
  private def merge[T](aList: List[T], bList: List[T], lt: (T, T) => Boolean): List[T] = {
    @tailrec
    def merge_aux(aList: List[T], bList: List[T], res: List[T]): List[T] =
      (aList, bList) match {
        case (Nil, _) => bList.reverse ::: res
        case (_, Nil) => aList.reverse ::: res
        case (a :: as, b :: bs) => if (lt(a, b)) merge_aux(as, bList, a :: res)
        else merge_aux(aList, bs, b :: res)
      }
    merge_aux(aList, bList, Nil).reverse
  }
}

