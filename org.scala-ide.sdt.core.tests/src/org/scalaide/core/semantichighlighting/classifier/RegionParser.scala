package org.scalaide.core.semantichighlighting.classifier

import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.Region
import scala.annotation.tailrec

object RegionParser {

  /**
   * This class represents a substring with an optional prefix and suffix.
   */
  case class EmbeddedSubstr(str: String, prefix: String = "", suffix: String = "") {
    private[RegionParser] val searchString = prefix + str + suffix
  }

  object EmbeddedSubstr {
    implicit def wrapAsEmbeddedSubstring(str: String) = EmbeddedSubstr(str)
  }


  /**
   * Extracts the regions marked by the given substrings.
   */
  def substrRegions(test: String, substrs: EmbeddedSubstr*): Map[IRegion, EmbeddedSubstr] = {
    @tailrec
    def regions(substr: EmbeddedSubstr, fromIndex: Int = 0, acc: Seq[IRegion] = Seq()): Seq[IRegion] = {
      val index = test.indexOf(substr.searchString, fromIndex)
      if (index < 0) {
        acc
      } else {
        val newAcc = acc :+ new Region(index + substr.prefix.length, substr.str.length)
        regions(substr, index + substr.searchString.length, newAcc)
      }

    }

    substrs.foldLeft(Map[IRegion, EmbeddedSubstr]()) { (acc, substr) =>
      acc ++ regions(substr).map(_ -> substr)
    }
  }

  /**
   * Search for regions delimited with a sign. In the default case the
   * delimited sign is a '$'.
   *
   * It is possible to put delimiter signs into the text by escaping them with
   * a '\'.
   *
   * @example {{{
   * scala> getRegions("""$abc$ def $ghi$""")
   * res200: Map[Region,String] = Map(Region(0,5) -> abc, Region(10,5) -> ghi)
   *
   * scala> getRegions("""$a\$bc$ de\$f $ghi$""")
   * res201: Map[Region,String] = Map(Region(0,6) -> a$bc, Region(12,5) -> ghi)
   *
   * scala> getRegions("""|a\|bc| de\|f |ghi|""", delimiter = '|')
   * res202: Map[Region,String] = Map(Region(0,6) -> a|bc, Region(12,5) -> ghi)
   * }}}
   * '''Note:''' When a delimiter sign is escaped, the resulting `Region` instances
   * are handled as there were no escape sign. This means that the String `$a\$b$`
   * is treated as  `$a$b$`.
   */
  def delimitedRegions(text: String, delimiter: Char = '$'): Map[IRegion, String] = {
    val sb = new StringBuilder
    var curPos = 0
    var offset = 0
    var regions = Map.empty[IRegion, String]

    while (curPos < text.length) {
      text.charAt(curPos) match {
        case '\\' if curPos + 1 < text.length && text.charAt(curPos+1) == delimiter =>
          if (!sb.isEmpty)
            sb += delimiter
          offset += 1
          curPos += 1
        case `delimiter` =>
          if (sb.isEmpty)
            sb += text.charAt(curPos)
          else {
            val start = curPos-sb.length
            val label = sb.substring(1, sb.length).trim
            sb.clear()
            regions += (new Region(start-offset, curPos-start+1) -> label)
          }
        case _ =>
          if (!sb.isEmpty)
            sb += text.charAt(curPos)
      }
      curPos += 1
    }
    require(sb.isEmpty, "odd number of '"+delimiter+"' signs in text")
    regions
  }
}
