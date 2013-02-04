package scala.tools.eclipse.semantichighlighting

import scala.tools.eclipse.jface.text.EmptyRegion

import org.eclipse.jface.text.IRegion

import org.eclipse.jface.text.Position
import org.eclipse.jface.text.Region

case class DocumentPositionsChange(toAdd: List[Position], toRemove: List[Position]) {

  private def findMinMax(positions: List[Position]): (Int, Int) = {
    def find(positions: List[Position], min: Int, max: Int): (Int, Int) = positions match {
      case Nil => (min, max)
      case pos :: rest =>
        val offset = pos.getOffset
        find(rest, Math.min(min, offset), Math.max(max, offset + pos.getLength))
    }
    find(positions, Int.MaxValue, Int.MinValue)
  }

  def createRegionChange(): IRegion = {
    val (min1, max1) = findMinMax(toAdd)
    val (min2, max2) = findMinMax(toRemove)
    
    val minStart = Math.min(min1, min2)
    val maxEnd = Math.max(max1, max2)
    
    if (minStart < maxEnd) new Region(minStart, maxEnd - minStart)
    else EmptyRegion
  }
}