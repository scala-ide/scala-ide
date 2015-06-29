package org.scalaide.core.semantichighlighting.classifier

import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.Region
import org.junit.Test
import org.junit.Assert._
import org.scalaide.core.semantichighlighting.classifier.RegionParser.EmbeddedSubstr
import org.scalaide.core.semantichighlighting.classifier.RegionParser.EmbeddedSubstr.wrapAsEmbeddedSubstring

class RegionParserTest {
  @Test
  def testDelimitedRegions(): Unit = {
    case class TestCase(input: String, expected: Map[IRegion, String])

    val testCases = Seq(
      TestCase("", Map()),
      TestCase("""$abc$ def $ghi$""", Map(new Region(0, 5) -> "abc", new Region(10, 5) -> "ghi")),
      TestCase("""$a\$bc$ de\$f $ghi$""", Map(new Region(0, 6) -> "a$bc", new Region(12, 5) -> "ghi")),
      TestCase("""\""", Map()))

    for(testCase <- testCases) {
      val actual = RegionParser.delimitedRegions(testCase.input)
      assertEquals(testCase.expected, actual)
    }
  }

  @Test
  def testSubstrRegions(): Unit = {
    case class TestCase(expected: Map[IRegion, EmbeddedSubstr], input: String, substrs: EmbeddedSubstr*)
    def toRegion(offset: Int, len: Int) = new Region(offset, len)

    val xStrY = EmbeddedSubstr("str", "x", "y")
    val xxxStr = EmbeddedSubstr("str", "xxx")
    val strYyy = EmbeddedSubstr("str", "", "yyy")

    val testCases = Seq(
      TestCase(Map(), ""),
      TestCase(Map(), "asdf", "xyz"),
      TestCase(Map(toRegion(0, 1) -> "a"), "a", "a"),
      TestCase(Map(toRegion(0, 1) -> "a", toRegion(1, 1) -> "a"), "aa", "a"),
      TestCase(Map(
          toRegion(1, 3) -> xStrY,
          toRegion(11, 3) -> xxxStr,
          toRegion(16, 3) -> strYyy,
          toRegion(5, 1) -> "5",
          toRegion(15, 1) -> "5"),
        "xstry56xxxxstr45stryyy",
        xStrY, xxxStr, strYyy, "5"))

     for (testCase <- testCases) {
       val actual = RegionParser.substrRegions(testCase.input, testCase.substrs :_*)
       assertEquals(testCase.expected, actual)
     }

  }
}
