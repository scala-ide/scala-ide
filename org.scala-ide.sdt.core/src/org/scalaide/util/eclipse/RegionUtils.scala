package org.scalaide.util.eclipse

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
import org.eclipse.jface.text.ITypedRegion
import java.lang.Math.max
import java.lang.Math.min
import org.scalaide.core.text.Document
import scala.collection.mutable.ListBuffer

/** Utility methods and extension classes around [[org.eclipse.jface.text.IRegion]]
 */
object RegionUtils {

  /** Creates an [[IRegion]] starting at `start` (inclusive) and ending at `end` (exclusive).
   */
  def regionOf(start: Int, end: Int): IRegion =
    new Region(start, end - start)

  /** Enrich [[org.eclipse.jdt.core.compiler.IProblem]].
   */
  implicit class RichProblem(private val problem: IProblem) extends AnyVal {

    /** Returns the region where this problem is situated
     */
    def toRegion: IRegion =
      new Region(problem.getSourceStart(), problem.getSourceEnd() - problem.getSourceStart())

    def length: Int = problem.getSourceEnd() - problem.getSourceStart()
    def start: Int = problem.getSourceStart()
    def end: Int = problem.getSourceEnd()
  }

  /** Enrich [[org.eclipse.jface.text.IRegion]].
   */
  implicit class RichRegion(private val region: IRegion) extends AnyVal {

    /** Returns `true` if this region intersects with the `other` region, i.e.: they have at least one character
     *  in common.
     *  Otherwise returns `false`.
     */
    def intersects(other: IRegion): Boolean =
      !(other.getOffset >= region.getOffset + region.getLength || other.getOffset + other.getLength - 1 < region.getOffset)

    /** Returns the section of the given array described by this region.
     *
     *  @note The region is trimmed by the bounds of `s`. It will return the empty
     *        string if `start` is outside the bounds of `s`.
     */
    def of(s: Array[Char]): String =
      s.slice(region.getOffset, region.getOffset + region.getLength).mkString

    /** Returns the section of the given string described by this region.
     *
     *  @note The region is trimmed by the bounds of `s`. It will return the empty
     *        string if `start` is outside the bounds of `s`.
     */
    def of(s: String): String =
      s.slice(region.getOffset, region.getOffset + region.getLength)

    /** Creates a [[scala.reflect.internal.util.RangePosition]] of this region.
     */
    def toRangePos(sourceFile: SourceFile): RangePosition = {
      val offset = region.getOffset()
      new RangePosition(sourceFile, offset, offset, offset + region.getLength())
    }

    def length: Int = region.getLength()
    def start: Int = region.getOffset()
    def end: Int = start+length

    /** Return a new Region with `f` applied to both start and end offsets.
     *
     *  @return A new region with `f(start)` as the starting point, and `f(start + offset)`
     *          as the end point, except if the length would be negative. In that case, the
     *          region is rounded up to a zero-length region.
     */
    def map(f: Int => Int): IRegion = {
      val newOffset = f(region.getOffset)
      val newLen = f(region.getOffset + region.getLength) - newOffset
      new Region(newOffset, Math.max(0, newLen))
    }

    /** Translate this region by applying `f` to the offset, and keeping the length
     *  unchanged.
     */
    def translate(f: Int => Int): IRegion = {
      new Region(f(region.getOffset), region.getLength)
    }

    def text(doc: Document): String =
      doc.textRange(region.start, region.end)

    def trim(doc: Document): IRegion =
      region.trimLeft(doc).trimRight(doc)

    def trimLeft(doc: Document): IRegion = {
      val s = text(doc)
      val len = region.length

      var i = 0
      while (i < len && Character.isWhitespace(s.charAt(i)))
        i += 1

      regionOf(region.start+i, region.end)
    }

    def trimRight(doc: Document): IRegion = {
      val s = text(doc)
      val len = region.length

      var i = len-1
      while (i >= 0 && Character.isWhitespace(s.charAt(i)))
        i -= 1

      regionOf(region.start, region.start+i+1)
    }
  }

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

  /** Enrich [[org.eclipse.jface.text.ITypedRegion]].
   */
  implicit class RichTypedRegion(val region: ITypedRegion) extends AnyVal {

    /** Returns a new Region, shifted of `n` characters from this one.
     */
    def shift(n: Int): ITypedRegion =
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
    def containsPositionExclusive(offset: Int): Boolean = {
      region.getOffset() <= offset && offset < (region.getOffset() + region.getLength())
    }

    /** Check if the given region is contained in this region.
     */
    def containsRegion(innerRegion: IRegion): Boolean = {
      containsPositionInclusive(innerRegion.getOffset()) && containsPositionInclusive(innerRegion.getOffset() + innerRegion.getLength())
    }

    /** Crops this region to not extend outside of the region defined by the given offset and length.
     *
     *  Returns Region(0,0) if this region doesn't intersect with the cropping region.
     */
    def crop(offset: Int, length: Int): ITypedRegion = {
      val rOffset = region.getOffset
      val rEnd = region.getOffset + region.getLength
      val cEnd = offset + length
      if (offset >= rEnd || rOffset >= cEnd)
        new TypedRegion(0, 0, region.getType)
      else if (offset <= rOffset && cEnd >= rEnd)
        region
      else {
        import Math._
        val newOffset = max(rOffset, offset)
        new TypedRegion(newOffset, min(rEnd, cEnd) - newOffset, region.getType)
      }
    }

  }

  /** Enrich [[List[org.eclipse.jface.text.TypedRegion]]].
   */
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

  /** Intersects between two lists of regions. The returned list contains the sections of the regions of list `a`
   *  which are also covered by the regions of list `b`.
   *  The regions in each list need to be ordered and non-overlapping, an [[llegalArgumentException]] is thrown otherwise.
   *  The regions in the returned list are ordered and non-overlapping.
   *
   *  @throw IllegalArgumentException if one of the list is not ordered or with non-overlapping regions
   */
  def intersect(a: List[TypedRegion], b: List[TypedRegion]): List[TypedRegion] = {
    checkInput(a, b)

    subtractImpl(a, subtractImpl(a, b, ListBuffer()), ListBuffer())
  }

  /** Subtracts a list of regions from another one. The returned list contains the sections of the regions of list `a`
   *  which are not covered by the regions of list `b`.
   *
   *  The regions in each list need to be ordered and non-overlapping, an [[IllegalArgumentException]] is thrown otherwise.
   *  The regions in the returned list are ordered and non-overlapping.
   *
   *  @throw IllegalArgumentException if one of the list is not ordered or with non-overlapping regions
   */
  def subtract(a: List[TypedRegion], b: List[TypedRegion]): List[TypedRegion] = {
    checkInput(a, b)

    subtractImpl(a, b, ListBuffer())
  }

  /** Merges two lists of regions.
   *  If the 2 input lists are ordered by their offset, the result is also ordered by
   *  the offset.
   *  The resulting list might have overlapping regions, if the input regions have overlaps.
   */
  def union(a: List[TypedRegion], b: List[TypedRegion]): List[TypedRegion] = {
    merge[TypedRegion](a, b, ((x, y) => x.getOffset() < y.getOffset()))
  }

  private def checkInput(a: List[TypedRegion], b: List[TypedRegion]): Unit = {
    if (!orderedAndNonOverlapping(a))
      throw new IllegalArgumentException("The regions of the first list are not ordered and non-ovelapping")
    if (!orderedAndNonOverlapping(b))
      throw new IllegalArgumentException("The regions of the second list are not ordered and non-ovelapping")
  }

  /** Returns `true` if the regions are ordered and non-overlapping, false otherwise.
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

  /** Implements the subtract method. `a` and `b` must be ordered and
   *  non-overlapping, `res` is the list that is returned.
   */
  @annotation.tailrec
  private def subtractImpl(a: List[TypedRegion], b: List[TypedRegion], res: ListBuffer[TypedRegion]): List[TypedRegion] = {
    (a, b) match {
      case (x :: xs, y :: ys) =>
        val xStart = x.getOffset()
        val xEnd = xStart + x.getLength() - 1
        val yStart = y.getOffset()
        val yEnd = yStart + y.getLength() - 1
        if (x.getLength() == 0) {
          subtractImpl(xs, b, res)
        } else if (xEnd < yStart) {
          //x: ___
          //y:      +++
          res += x
          subtractImpl(xs, b, res)
        } else if (yEnd < xStart) {
          //x:      ___
          //y: +++
          subtractImpl(a, ys, res)
        } else if (x.containsRegion(y)) { // x contains y
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
          res ++= newElem
          subtractImpl(producedElem ::: xs, ys, res)
        } else if (y.containsRegion(x)) { // y contains x
          //x:    -----
          //y:   +++++++
          subtractImpl(xs, b, res)
        } else if (x.containsPositionExclusive(yEnd)) {
          //x:  -------
          //y: ++++
          val producedElem = new TypedRegion(yEnd + 1, xEnd - yEnd, x.getType())
          subtractImpl(producedElem :: xs, ys, res)
        } else if (x.containsPositionExclusive(yStart)) {
          //x:  -------
          //y:      ++++++
          val newElem = new TypedRegion(xStart, yStart - xStart, x.getType())
          res += newElem
          subtractImpl(xs, b, res)
        } else {
          throw new RuntimeException("Unhandled case! Impossible!")
        }

      case (xl, Nil) =>
        res ++= xl
        res.toList

      case (Nil, _) =>
        res.toList
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
