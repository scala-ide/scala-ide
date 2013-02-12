package scala.tools.eclipse.semantichighlighting

import java.util.Arrays.{ binarySearch, copyOfRange }

import scala.collection.mutable
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes

import org.eclipse.jface.text.DocumentEvent
import org.eclipse.jface.text.IRegion

/** This class keeps track of semantic positions and it's used by the text's presentation to apply semantic highlighting
  * styles in the editor.
  *
  * There are two important facts to understand about this implementation:
  *
  * 1) All accesses that mutate the positions held by `this` instance are run inside the UI Thread. The natural consequence
  * of this is that to get a consistent view of the tracked position your code has to run within the UI Thread.
  *
  * 2) Computing the new positions is an expensive operation, particularly for big files. Furthermore, it's not acceptable
  * to compute the new positions in the UI Thread. The solution is to adopt an ''optimistic concurrency control'' strategy
  * (@see http://en.wikipedia.org/wiki/Optimistic_concurrency_control).
  * Basically, the new positions are computed outside of the UI Thread and the new positions can replace the old ones only
  * and only if no change happen while the new positions where being computed. If a change is detected, the positions are
  * discarded (this is ok because a new semantic highlighting job will start as soon as the user pause, and the java
  * reconciler kicks-in).
  */
private[semantichighlighting] class PositionsTracker extends HasLogger {

  @volatile private var positions: Array[Position] = Array.empty

  @volatile private var trackedPositionsChanged = false

  def startComputingNewPositions(): Unit = { trackedPositionsChanged = false }

  def isDirty: Boolean = trackedPositionsChanged

  /** Compares the `newPositions` with the current `positions` and return the sequence of positions
    * that have been added and removed since last reconciliation.
    *
    * @param `newPositions` The freshly computed positions.
    * @return A container holding the added and removed positions since last reconciliation.
    */
  def createPositionsChange(newPositions: List[Position]): PositionsChange = {
    /* Filtering out deleted positions here is important, failing to do so can cause half-colored identifiers.
     * The reason is that deleted positions should not be considered when computing the damaged region that is 
     * used to invalidate the text presentation. Failing to do so can result in the computed damaged region to 
     * partially remove a keyword's coloring style.   
     */
    val existingPositions = positions.filterNot(_.isDeleted())
    val existingPositionsByOffset = existingPositions.groupBy(_.getOffset)

    val positionsToAdd = mutable.ListBuffer.empty[Position]
    val positionsToRemove = mutable.ListBuffer.empty[Position] ++ existingPositions

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
  def reset(): Unit = {
    trackedPositionsChanged = true
    positions = Array.empty
  }

  /** Replace the currently held `positions` with the passed `newPositions`.
    *
    * @note `newPositions` are expected to be sorted.
    * @note This method must always be called within the UI Thread.
    *
    * @precondition `positionsChanged` is `false`
    * @param newPositions The new semantic positions (that will be colored in the editor)
    */
  def swapPositions(newPositions: Array[Position]): Unit = {
    if (isDirty)
      logger.error("Error while performing semantic highlighting. Attempting to swap posions on a " +
        "not up-to-date state. Please report a bug at " + ScalaPlugin.IssueTracker)
    else positions = newPositions
  }

  /** Return all currently tracked `positions` in the passed `region`.
    *
    * @note For performance reasons, deleted positions are not filtered out. Clients of this method are
    * expected to filter the deleted positions themselves
    *
    * @note This method must always be called within the UI Thread.
    *
    * @return The sequence of positions that are included in the passed `region`
    */
  def positionsInRegion(region: IRegion): Array[Position] = {
    if (region.getLength() == 0 || positions.length == 0) Array.empty[Position]
    else {
      // `positions` are sorted so here we first find the lower and upper index. 
      // This matters for large files, i.e., when `positions` is > 10K
      def findIndex(position: Position): Int = {
        /* @see java.util.Arrays.binarySearch documentation.*/
        val indexOrMirroredInsertionPoint = binarySearch(positions, position, Position.ByOffset)
        val index = Math.max(-1 - indexOrMirroredInsertionPoint, indexOrMirroredInsertionPoint)
        Math.min(index, positions.length)
      }

      val dummyLowerPosition = new Position(region.getOffset, 0, null, false)
      val lowerIndex = findIndex(dummyLowerPosition)

      val dummyUpperPosition = new Position(region.getOffset + region.getLength, 0, null, false)
      val upperIndex = findIndex(dummyUpperPosition)

      copyOfRange(positions, lowerIndex, upperIndex)
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

    for (position <- positions) {
      val posOffset = position.getOffset
      val posEnd = posOffset + position.getLength

      if (editionOffset > posEnd) { /* nothing to do because the current `position` is not affected by the triggered edition change */ }
      else {
        trackedPositionsChanged = true
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
    for (position <- positions) {
      if (position.kind == kind) {
        trackedPositionsChanged = true
        position.delete()
      }
    }
  }
}