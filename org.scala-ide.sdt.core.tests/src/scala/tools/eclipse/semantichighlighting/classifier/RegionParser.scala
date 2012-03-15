package scala.tools.eclipse.semantichighlighting.classifier

/**
 * Search for regions delimited with $ signs.
 */
object RegionParser {

    def getRegions(text: String): Map[Region, String] = {
      var regions = Map[Region, String]()
      var regionStartOpt: Option[Int] = None

      for (('$', pos) <- text.zipWithIndex)
        regionStartOpt match {
          case Some(regionStart) =>
            regionStartOpt = None
            val region = Region(regionStart, pos - regionStart + 1)
            val regionLabel = text.substring(regionStart + 1, pos).trim
            regions += (region -> regionLabel)
          case None =>
            regionStartOpt = Some(pos)
        }

      require(regionStartOpt.isEmpty)
      regions
    }

  }