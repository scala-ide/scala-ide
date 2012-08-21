package scala.tools.eclipse.semantichighlighting.classifier

object RegionParser {

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
  def getRegions(text: String, delimiter: Char = '$'): Map[Region, String] = {
    val sb = new StringBuilder
    var curPos = 0
    var offset = 0
    var regions = Map.empty[Region, String]

    while (curPos < text.length) {
      text.charAt(curPos) match {
        case '\\' if text.charAt(curPos+1) == delimiter =>
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
            regions += (Region(start-offset, curPos-start+1) -> label)
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