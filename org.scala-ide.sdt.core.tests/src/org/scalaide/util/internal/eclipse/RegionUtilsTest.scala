package org.scalaide.util.internal.eclipse

import org.junit.Test
import org.junit.Assert._
import org.eclipse.jface.text.TypedRegion

class RegionUtilsTest {

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

  /** |       ++++
   *  | ----
   */
  @Test
  def overlapBefore {
    testNoOverlap(13, 7, 5, 5)
  }

  /** |     ++++
   *  | ----
   */
  @Test
  def overlapBeforeTouching {
    testNoOverlap(13, 7, 5, 8)
  }

  /** |   ++++
   *  | ----
   */
  @Test
  def overlapOverBeginning {
    testOverlap(13, 7, 11, 5)
  }

  /** | ++++++
   *  |  ----
   */
  @Test
  def overlapInsideSmaller {
    testOverlap(13, 7, 14, 3)
  }

  /** | ++++
   *  | ----
   */
  @Test
  def overlapSame {
    testOverlap(13, 7, 14, 3)
  }

  /** |  ++++
   *  | ------
   */
  @Test
  def overlapOverLarger {
    testOverlap(13, 7, 11, 11)
  }

  /** | ++++
   *  |   ----
   */
  @Test
  def overlapOverEnd {
    testOverlap(13, 7, 17, 5)
  }

  /** | ++++
   *  |     ----
   */
  @Test
  def overlapAfterTouching {
    testNoOverlap(13, 7, 20, 5)
  }

  /** | ++++
   *  |       ----
   */
  @Test
  def overlapAfter {
    testNoOverlap(13, 7, 22, 5)
  }

  private def testNoOverlap(offsetA: Int, lengthA: Int, offsetB: Int, lengthB: Int) {
    import RegionUtils._
    assertFalse(s"($offsetA, $lengthA) should not overlap with ($offsetB, $lengthB)", new TypedRegion(offsetA, lengthA, "A").overlapsWith(new TypedRegion(offsetB, lengthB, "B")))
  }

  private def testOverlap(offsetA: Int, lengthA: Int, offsetB: Int, lengthB: Int) {
    import RegionUtils._
    assertTrue(s"($offsetA, $lengthA) should overlap with ($offsetB, $lengthB)", new TypedRegion(offsetA, lengthA, "A").overlapsWith(new TypedRegion(offsetB, lengthB, "B")))
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
  def cropLarger {
    testCrop(22, 9, 18, 15, 22, 9)
  }

  @Test
  def cropSameSize {
    testCrop(20, 7, 20, 7, 20, 7)
  }

  @Test
  def cropSmaller {
    testCrop(21, 8, 23, 4, 23, 4)
  }

  @Test
  def cropOverStart {
    testCrop(23, 6, 18, 8, 23, 3)
  }

  @Test
  def cropOverEnd {
    testCrop(24, 7, 27, 8, 27, 4)
  }

  @Test
  def cropBeforeDisjointed {
    testCrop(25, 8, 12, 9, 0, 0)
  }

  @Test
  def cropAfterDisjointed {
    testCrop(26, 7, 40, 6, 0, 0)
  }

  @Test
  def cropBeforeContiguous {
    testCrop(27, 9, 18, 9, 0, 0)
  }

  @Test
  def cropAfterContiguous {
    testCrop(28, 6, 34, 2, 0, 0)
  }

  def testCrop(startOffset: Int, startLength: Int, cropOffset: Int, cropLength: Int, expectedOffset: Int, expectedLength: Int) {
    import RegionUtils._
    val startRegion = new TypedRegion(startOffset, startLength, "crop")
    val expectedRegion = new TypedRegion(expectedOffset, expectedLength, "crop")
    val croppedRegion = startRegion.crop(cropOffset, cropLength)
    assertEquals("Incorrect shifted region", expectedRegion, croppedRegion)
  }

}