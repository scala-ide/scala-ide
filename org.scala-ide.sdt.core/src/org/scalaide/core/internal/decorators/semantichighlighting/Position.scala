package org.scalaide.core.internal.decorators.semantichighlighting

import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolInfo
import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolTypes
import org.eclipse.jface.text.{ Position => TextPosition }

/** Represents a semantically colored position in the editor.
  *
  * @note This class is thread-safe.
  */
class Position (
  offset: Int,
  length: Int,
  val kind: SymbolTypes.SymbolType,
  val deprecated: Boolean,
  val inInterpolatedString: Boolean) extends TextPosition(offset, length) {

  /** Lock used to protect concurrent access to `this` instance.*/
  private val lock: AnyRef = new Object

  def shouldStyle = lock.synchronized { deprecated || inInterpolatedString }

  override def hashCode(): Int = lock.synchronized { super.hashCode() }

  override def delete(): Unit = lock.synchronized { super.delete() }

  override def undelete(): Unit = lock.synchronized { super.undelete() }

  override def equals(that: Any): Boolean = that match {
    // This implementation of `equals` is NOT symmetric.
    case that: Position =>
      lock.synchronized { super.equals(that) && kind == that.kind && deprecated == that.deprecated && inInterpolatedString == that.inInterpolatedString && isDeleted() == that.isDeleted() }
    case _ => false
  }

  override def getLength(): Int = lock.synchronized { super.getLength() }

  override def getOffset(): Int = lock.synchronized { super.getOffset() }

  override def includes(index: Int): Boolean = lock.synchronized { super.includes(index) }

  override def overlapsWith(rangeOffset: Int, rangeLength: Int): Boolean = lock.synchronized { super.overlapsWith(rangeOffset, rangeLength) }

  override def isDeleted(): Boolean = lock.synchronized { super.isDeleted() }

  override def setLength(length: Int): Unit = lock.synchronized { super.setLength(length) }

  override def setOffset(offset: Int): Unit = lock.synchronized { super.setOffset(offset) }

  override def toString(): String = lock.synchronized { super.toString() }
}

object Position {
  implicit object ByOffset extends Ordering[Position] {
    override def compare(x: Position, y: Position): Int = x.getOffset() - y.getOffset()
  }

  def from(symbolInfos: List[SymbolInfo]): List[Position] = {
    (for {
      SymbolInfo(symbolType, regions, isDeprecated, inInterpolatedString) <- symbolInfos
      region <- regions
      if region.getLength > 0
    } yield new Position(region.getOffset, region.getLength, symbolType, isDeprecated, inInterpolatedString))
  }
}
