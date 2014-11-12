package org.scalaide.core.semantichighlighting.classifier

import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.Region
import org.junit.Test
import org.junit.Assert._

private case class GetRegionsTestCase(input: String, expected: Map[IRegion, String])

class RegionParserTest {

  private val getRegionsTestCases = Seq(
      GetRegionsTestCase("", Map()),
      GetRegionsTestCase("""$abc$ def $ghi$""", Map(new Region(0, 5) -> "abc", new Region(10, 5) -> "ghi")),
      GetRegionsTestCase("""$a\$bc$ de\$f $ghi$""", Map(new Region(0, 6) -> "a$bc", new Region(12, 5) -> "ghi")),
      GetRegionsTestCase("""\""", Map()))


  @Test
  def getRegions() {
    for(testCase <- getRegionsTestCases) {
      val actual = RegionParser.getRegions(testCase.input)
      assertEquals(testCase.expected, actual)
    }
  }
}