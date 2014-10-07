package org.scalaide.util.eclipse

import org.junit.Test
import org.junit.Assert._
import org.eclipse.jface.text.TypedRegion
import org.eclipse.jface.text.Region

class RegionUtilsTest {

  import RegionUtilsTest._

  @Test
  def substractAContainsB {

    val a = List(new TypedRegion(0, 16, "A"))
    val b = List(new TypedRegion(1, 15, "B"))

    val expected = List(new TypedRegion(0, 1, "A"))

    val actual = RegionUtils.subtract(a, b)

    assertEquals("Wrong result", expected, actual)

  }

  @Test
  def substractEmtpies {
    val a = List(new TypedRegion(0, 0, "A"))
    val b = List(new TypedRegion(0, 0, "B"))

    val expected = Nil

    val actual = RegionUtils.subtract(a, b)

    assertEquals("Wrong result", expected, actual)
  }

  @Test
  def substract_issue_123 {
    val a = List(new TypedRegion(808, 93, "A"), new TypedRegion(908, 52, "A"))
    val b = List(new TypedRegion(807, 20, "B"), new TypedRegion(881, 13, "B"), new TypedRegion(899, 3, "B"))

    val expected = List(new TypedRegion(827, 54, "A"), new TypedRegion(894, 5, "A"), new TypedRegion(908, 52, "A"))

    val actual = RegionUtils.subtract(a, b)

    assertEquals("Wrong result", expected, actual)

  }

  @Test(expected = classOf[IllegalArgumentException])
  def substractWithOverlappingList {
    val a = List(new TypedRegion(0, 10, "A"), new TypedRegion(9, 2, "B")) // overlapping
    val b = List(new TypedRegion(1, 1, "C"))

    RegionUtils.subtract(a, b)
  }

  @Test(expected = classOf[IllegalArgumentException])
  def intersectWithNonOrderedList {
    val a = List(new TypedRegion(1, 1, "C"))
    val b = List(new TypedRegion(12, 10, "A"), new TypedRegion(5, 2, "B")) // non-ordered

    RegionUtils.intersect(a, b)
  }

  private def test(cas: UseCase, testF: UseCase => Unit) = {
    testF(cas)
  }

  @Test
  def overlapBefore {
    test(CaseBefore, testNoOverlap)
  }

  @Test
  def overlapBeforeTouching {
    test(CaseBeforeTouching, testNoOverlap)
  }

  @Test
  def overlapOverBeginning {
    test(CaseOverBeginning, testOverlap)
  }

  @Test
  def overlapInsideSmaller {
    test(CaseInsideSmaller, testOverlap)
  }

  @Test
  def overlapSame {
    test(CaseSame, testOverlap)
  }

  @Test
  def overlapOverLarger {
    test(CaseOverLarger, testOverlap)
  }

  @Test
  def overlapOverEnd {
    test(CaseOverEnd, testOverlap)
  }

  @Test
  def overlapAfterTouching {
    test(CaseAfterTouching, testNoOverlap)
  }

  @Test
  def overlapAfter {
    test(CaseAfter, testNoOverlap)
  }

  private def testNoOverlap(cas: UseCase) {
    import RegionUtils.RichRegion
    assertFalse(s"(${cas.offsetA}, ${cas.lengthA}) should not overlap with (${cas.offsetB}, ${cas.lengthB})", new Region(cas.offsetA, cas.lengthA).intersects(new Region(cas.offsetB, cas.lengthB)))
  }

  private def testOverlap(cas: UseCase) {
    import RegionUtils.RichRegion
    assertTrue(s"(${cas.offsetA}, ${cas.lengthA}) should not overlap with (${cas.offsetB}, ${cas.lengthB})", new Region(cas.offsetA, cas.lengthA).intersects(new Region(cas.offsetB, cas.lengthB)))
  }

  @Test
  def shiftPositive {
    testShift(14, 8, 5, 19, 8)
  }

  @Test
  def shiftZero {
    testShift(15, 7, 0, 15, 7)
  }

  @Test
  def shiftNegative {
    testShift(16, 6, -4, 12, 6)
  }

  private def testShift(startOffset: Int, startLength: Int, shift: Int, expectedOffset: Int, expectedLength: Int) {
    import RegionUtils._
    val startRegion = new TypedRegion(startOffset, startLength, "shift")
    val expectedRegion = new TypedRegion(expectedOffset, expectedLength, "shift")
    val shiftedRegion = startRegion.shift(shift)
    assertEquals("Incorrect shifted region", expectedRegion, shiftedRegion)
  }

  @Test
  def coreBefore {
    test(CaseBefore, testCrop(0,0))
  }

  @Test
  def coreBeforeTouching {
    test(CaseBeforeTouching, testCrop(0,0))
  }

  @Test
  def coreOverBeginning {
    test(CaseOverBeginning, testCrop(13, 3))
  }

  @Test
  def coreInsideSmaller {
    test(CaseInsideSmaller, testCrop(14, 3))
  }

  @Test
  def coreSame {
    test(CaseSame, testCrop(13, 7))
  }

  @Test
  def coreOverLarger {
    test(CaseOverLarger, testCrop(13, 7))
  }

  @Test
  def coreOverEnd {
    test(CaseOverEnd, testCrop(17,3))
  }

  @Test
  def coreAfterTouching {
    test(CaseAfterTouching, testCrop(0,0))
  }

  @Test
  def coreAfter {
    test(CaseAfter, testCrop(0,0))
  }

  def testCrop(expectedOffset: Int, expectedLength: Int)(cas: UseCase) {
    import RegionUtils.RichTypedRegion
    val startRegion = new TypedRegion(cas.offsetA, cas.lengthA, "crop")
    val expectedRegion = new TypedRegion(expectedOffset, expectedLength, "crop")
    val croppedRegion = startRegion.crop(cas.offsetB, cas.lengthB)
    assertEquals("Incorrect shifted region", expectedRegion, croppedRegion)
  }

}

object RegionUtilsTest {

  case class UseCase(offsetA: Int, lengthA: Int, offsetB: Int, lengthB: Int)

  /** |       ++++
   *  | ----
   */
  val CaseBefore = UseCase(13, 7, 5, 5)

  /** |     ++++
   *  | ----
   */
  val CaseBeforeTouching = UseCase(13, 7, 5, 8)

  /** |   ++++
   *  | ----
   */
  val CaseOverBeginning = UseCase(13, 7, 11, 5)

  /** | ++++++
   *  |  ----
   */
  val CaseInsideSmaller = UseCase(13, 7, 14, 3)

  /** | ++++
   *  | ----
   */
  val CaseSame = UseCase(13, 7, 13, 7)

  /** |  ++++
   *  | ------
   */
  val CaseOverLarger = UseCase(13, 7, 11, 11)

  /** | ++++
   *  |   ----
   */
  val CaseOverEnd = UseCase(13, 7, 17, 5)

  /** | ++++
   *  |     ----
   */
  val CaseAfterTouching = UseCase(13, 7, 20, 5)

  /** | ++++
   *  |       ----
   */
  val CaseAfter = UseCase(13, 7, 22, 5)


}