package scala.tools.eclipse.semantichighlighting

import scala.collection.mutable
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes

import org.eclipse.jface.text.DocumentEvent
import org.eclipse.jface.text.IRegion

/** This class keeps track of semantic positions and it's used by the text's presentation to
  * color the semantic positions in the editor.
  *
  * There are two extremely important fact to understand about this implementation:
  *
  * First, all accesses that mutate the held `positions` must be run within the UI Thread.
  *
  * Second, The semantic reconciler optimistically computes the new positions. It is optimistic
  * because the new positions are computed on the sematic reconciler thread, while the user could
  * start typing and hence invalidate the freshly computed positions (because they were computed on
  * a not up-to-date compilation's unit content). Whenever the user starts typing, the method
  * `updatePositions` is called and `positionsChanged` is set to `true`. This is done to inform the
  * semantic reconciler that it should drop the work he has done. Also, keep in mind that a new
  * semantic reconciler run will be started as soon as the user pause, and the java reconciler kick-in.
  */
private[semantichighlighting] class PositionsTracker extends HasLogger {

  @volatile private var positions: Array[Position] = Array.empty

  @volatile private var positionsChanged = false

  def startComputingNewPositions(): Unit = { positionsChanged = false }

  def isPositionsChanged: Boolean = positionsChanged

  /** Compares the `newPositions` with the current `positions` and return the sequence of positions
    * that have been added and removed since last reconciliation.
    *
    * @param `newPositions` The freshly computed positions.
    * @return A container holding the added and removed positions since last reconciliation.
    */
  def createPositionsChange(newPositions: List[Position]): PositionsChange = {
    val existingPositionsByOffset = positions.groupBy(_.getOffset)

    val positionsToAdd = mutable.ListBuffer.empty[Position]
    val positionsToRemove = mutable.ListBuffer.empty[Position] ++ positions

    for {
      newPos <- newPositions
      offset = newPos.getOffset()
    } {
      // sanity check
      if (newPos.isDeleted()) {
        logger.error("Encountered position deleted during semantic highlighting. Please report a bug at " + ScalaPlugin.IssueTracker)
      }
      else {
        existingPositionsByOffset.get(offset) match {
          case None =>
            // No positions existed at the given offset, hence it's a new position.
            positionsToAdd += newPos

          case Some(existingPositions) =>
            for (oldPos <- existingPositions) {
              if (newPos == oldPos) {
                // Old position is the same as new one, so no need to remove it.
                positionsToRemove -= newPos
              }
              else positionsToAdd += newPos
            }
        }
      }
    }

    PositionsChange(positionsToAdd.toList, positionsToRemove.toList)
  }

  /** @note This method must always be called within the UI Thread. */
  def dispose(): Unit = { positions = Array.empty }

  /** Replace the currently held `positions` with the passed `newPositions`.
    *
    * @note `newPositions` are expected to be sorted.
    * @note This method must always be called within the UI Thread.
    *
    * @precondition `positionsChanged` is `false`
    * @param newPositions The new semantic positions (that will be colored in the editor)
    */
  def swapPositions(newPositions: Array[Position]): Unit = {
    if (positionsChanged)
      logger.error("Error while performing semantic highlighting. Attempting to swap posions on a " +
        "not up-to-date state. Please report a bug at " + ScalaPlugin.IssueTracker)
    else positions = newPositions
  }

  /** @note This method must always be called within the UI Thread.
    * @return The sequence of positions that are included in the passed `region`
    */
  def positionsInRegion(region: IRegion): List[Position] = {
    // TODO: positions are sorted so this could be optimized by first locating the lower and upper 
    //       index (simply by using a binary search algorithm). This could matter for large files.
    if (region.getLength() == 0 || positions.length == 0) Nil
    else {
      val regionOffset = region.getOffset()
      val regionLenght = region.getLength()
      val regionEnd = regionOffset + regionLenght

      val bf = new mutable.ListBuffer[Position]
      var index = 0
      var position = positions(index)
      while (position.getOffset < regionEnd && index < positions.length) {
        position = positions(index)
        if ((position.getOffset >= regionOffset) && (position.getOffset <= regionEnd) && !position.isDeleted())
          bf += position
        index += 1
      }

      bf.toList
    }
  }

  /** Deletes all positions included in the `event`'s region. Positions that are after the `event`'s
    * region are shifted.
    *
    * @note This method must always be called within the UI Thread.
    */
  def updatePositions(event: DocumentEvent): Unit = {
    val editionOffset = event.getOffset
    val editionLength = event.getLength
    val editionEnd = editionOffset + editionLength
    val newText = event.getText()
    val newLength = Option(newText) map (_.length) getOrElse 0

    for {
      index <- 0 until positions.length
      position = positions(index)
    } {
      val posOffset = position.getOffset
      val posEnd = posOffset + position.getLength

      if (editionOffset > posEnd) { /* nothing to do because the current `position` is not affected by the triggered edition change */ }
      else {
        positionsChanged = true
        if (editionEnd < posOffset) {
          // edition change occurred *before* the current `position`, which implies that the `position`'s offset needs to be shifted
          val delta = newLength - editionLength
          position.setOffset(posOffset + delta)
        }
        else {
          // The edit affected the current `position`, hence let's delete it
          position.delete()
        }
      }
    }
  }

  /** Deletes all positions of the passed `kind`.
    *
    * @note This method must always be called within the UI Thread.
    */
  def deletesPositionsOfType(kind: SymbolTypes.SymbolType): Unit = {
    for {
      index <- 0 until positions.length
      position = positions(index)
    } {
      if (position.kind == kind) {
        positionsChanged = true
        position.delete()
      }
    }
  }
}
