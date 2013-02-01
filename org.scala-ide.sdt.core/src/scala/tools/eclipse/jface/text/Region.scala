package scala.tools.eclipse.jface.text

import org.eclipse.jface.text.IRegion

class RegionOps(region: IRegion) {
  def of(s: Array[Char]): String = s.slice(region.getOffset, region.getOffset + region.getLength).mkString
}

object RegionOps {
  implicit def region2regionOps(region: IRegion): RegionOps = new RegionOps(region)
}