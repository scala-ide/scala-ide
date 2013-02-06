package scala.tools.eclipse.semantichighlighting

import scala.actors.Actor
import scala.collection.mutable
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.util.withDocument

import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jface.text.BadLocationException
import org.eclipse.jface.text.BadPositionCategoryException
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.Region

/** This class is responsible of updating the positions in the document (associated with the passed `positionCategory`),
  * whenever the document's content is changed.
  */
class DocumentPositions(textPresentation: TextPresentationHighlighter, positionCategory: String) extends Actor with HasLogger {
  import DocumentPositions._
  override final def act(): Unit = loop {
    react {
      case UpdatePositions(monitor: IProgressMonitor, newPositions) => updateDocumentPositions(monitor, newPositions)
      case DocumentChanged(editOffset, editLength, newText) => documentChanged(new Region(editOffset, editLength), newText)
      case PoisonPill => exit()
    }
  }

  private def updateDocumentPositions(monitor: IProgressMonitor, newPositions: List[Position]): Unit = {
    for {
      positionsChange <- createDocumentPositionsChange(newPositions)
      if !monitor.isCanceled()
    } {
      updateDocumentPositions(positionsChange)
      textPresentation.updateTextPresentation(positionsChange)
    }
  }

  /** Compute the list of positions that have to be added and removed from the document's model.
    * It does so by comparing the passed `newPositions` and the existing positions held by the
    * `sourceViewer`'s document.
    * The returned object is used to computed the editor's damaged region. This region is then
    * used to perform a localized editor's style update.
    *
    * @param newPositions The freshly computed positions that should be stored in the `sourceViewer`'s document.
    * @return A data object holding the sequence of positions that should be added and removed from this `sourceViewer`'s document.
    */
  private def createDocumentPositionsChange(newPositions: List[Position]): Option[DocumentPositionsChange] = {
    withDocument(sourceViewer) { document =>
      val existingPositions = {
        try document.getPositions(positionCategory)
        catch {
          case e: BadPositionCategoryException =>
            logger.error(e) // should never happen
            Array.empty[Position]
        }
      }
      val existingPositionsByOffset = existingPositions.groupBy(_.getOffset)

      val positionsToAdd = mutable.ListBuffer.empty[Position]
      val positionsToRemove = mutable.ListBuffer.empty[Position] ++ existingPositions

      for {
        newPos <- newPositions
        offset = newPos.getOffset()
      } {
        // sanity check
        if (newPos.isDeleted) {
          logger.error("Encountered position deleted during semantic highlighting. Please report a bug at " + ScalaPlugin.IssueTracker)
        }
        else {
          existingPositionsByOffset.get(offset) match {
            case None =>
              // No positions existed at the given offset, hence it's a new position!
              positionsToAdd += newPos

            case Some(existingPositions) =>
              for (oldPos <- existingPositions) {
                if (newPos == oldPos) {
                  // Old position is the same as new one, so keep the position in the document's model
                  positionsToRemove -= newPos
                }
                else positionsToAdd += newPos
              }
          }
        }
      }

      DocumentPositionsChange(positionsToAdd.toList, positionsToRemove.toList)
    }
  }

  /** Uses the passed `positionsChange` to update the `sourceViewer`'s document. */
  private def updateDocumentPositions(positionsChange: DocumentPositionsChange): Unit = {
    withDocument(sourceViewer) { document =>
      positionsChange.toAdd.foreach { pos =>
        try document.addPosition(positionCategory, pos)
        catch {
          case ble: BadLocationException =>
            pos.delete() // delete the invalid position
            logger.debug("The position " + pos + " doesn't exist in the document. Functionality should " +
              "not be affected by this exception, which is reported only for debugging purposes.", ble)
          case bpce: BadPositionCategoryException =>
            logger.error(bpce) // should never happen
        }
      }
      positionsChange.toRemove.foreach { pos =>
        try document.removePosition(positionCategory, pos)
        catch {
          case bpce: BadPositionCategoryException =>
            logger.error(bpce) // should never happen
        }
      }
    }
  }

  private def documentChanged(region: IRegion, newText: String): Unit = withDocument(sourceViewer) { document =>
    val editionOffset = region.getOffset
    val editionLength = region.getLength
    val editionEnd = editionOffset + editionLength
    val newLength = Option(newText) map (_.length) getOrElse 0

    val positions = {
      try document.getPositions(positionCategory)
      catch {
        case e: BadPositionCategoryException =>
          logger.error(e) // should never happen
          Array.empty[Position]
      }
    }

    for (position <- positions) {
      val posOffset = position.getOffset
      val posEnd = posOffset + position.getLength

      if (editionOffset > posEnd) { /* nothing to do because the current `position` is not affected by the triggered edition change */ }
      else if (editionEnd < posOffset) {
        // edition change occurred *before* the current `position`, which implies that the `position`'s offset needs to be shifted
        val delta = newLength - editionLength
        position.setOffset(posOffset + delta)
      }
      else {
        // The edition affected the current `position`, hence let's delete it.
        // Ideally, we would like to remove the position from the `document` via `document.removePosition(category, position)`. 
        // However, this is not safe because in the UI-thread we could be concurrently calling `document.addPosition` for the 
        // current `position` (with some unlucky timing). Therefore, we only side-effect the `position`'s attributes, and let 
        // code that is running in the UI-thread taking care of physically removing the deleted positions from the document's model.
        position.delete()
      }
    }
  }

  private def sourceViewer = textPresentation.sourceViewer

  override def exceptionHandler = {
    case e: Exception =>
      logger.info("Unahndled exception was caught by actor " + DocumentPositions.this + ". Please report a bug at " + ScalaPlugin.IssueTracker + ". " +
        "Trying to self-healing, if semantic highlighting doesn't work as expected, please close and re-open the editor.", e)
  }
}

object DocumentPositions {
  trait Msg
  case class UpdatePositions(monitor: IProgressMonitor, newPositions: List[Position]) extends Msg
  case class DocumentChanged(editOffset: Int, editLength: Int, newText: String) extends Msg
  case object PoisonPill extends Msg
}