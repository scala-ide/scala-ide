package scala.tools.eclipse.jface.text

import org.eclipse.jface.text.IRegion

//TODO: Transform this into an implicit value class as soon as we drop 2.9 support!
class RegionOps(region: IRegion) {
  def of(s: Array[Char]): String = s.slice(region.getOffset, region.getOffset + region.getLength).mkString
}

object RegionOps {
  implicit def region2regionOps(region: IRegion): RegionOps = new RegionOps(region)
  
  // TODO: Remove this method as soon as `RegionOps` is transformed in an implicit value class. 
  //       https://github.com/scala-ide/scala-ide/pull/292/files#r2873974 gives some context on why we do it this way for the moment. 
  def of(region: IRegion, s: Array[Char]): String = s.slice(region.getOffset, region.getOffset + region.getLength).mkString
}