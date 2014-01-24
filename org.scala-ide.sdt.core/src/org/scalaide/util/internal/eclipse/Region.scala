package scala.tools.eclipse.jface.text

import org.eclipse.jface.text.IRegion

object RegionOps {
  implicit class RichRegion(val _region: IRegion) extends AnyVal {
    def of(s: Array[Char]): String = s.slice(_region.getOffset, _region.getOffset + _region.getLength).mkString
  }
}