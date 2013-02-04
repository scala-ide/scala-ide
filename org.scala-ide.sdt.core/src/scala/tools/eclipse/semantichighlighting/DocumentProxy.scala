package scala.tools.eclipse.semantichighlighting

import scala.collection.mutable
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.jface.text.EmptyRegion
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.util.withDocument

import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jface.text.BadLocationException
import org.eclipse.jface.text.BadPositionCategoryException
import org.eclipse.jface.text.DocumentEvent
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IDocumentListener
import org.eclipse.jface.text.IPositionUpdater
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextInputListener
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.source.ISourceViewer

/** This class is responsible of updating the highlighted positions in the document (associated with the passed `positionCategory`),
  * whenever the document's content is changed.
  *
  * This class is thread-safe.
  */
private[semantichighlighting] class DocumentProxy(sourceViewer: ISourceViewer, reconciler: Job, positionCategory: String) extends HasLogger {

  private val documentInputChangeListener = new DocumentProxy.DefaultTextInputListener(DocumentProxy.this, reconciler)
  private val positionUpdater = new DocumentProxy.HighlightedPositionUpdater(sourceViewer, positionCategory)
  private val documentChangeListener = new DocumentProxy.DefaultDocumentListener(reconciler)

  def initialize(): Unit = {
    sourceViewer.addTextInputListener(documentInputChangeListener)
    manageDocument(sourceViewer.getDocument)
  }

  def dispose(): Unit = {
    sourceViewer.removeTextInputListener(documentInputChangeListener)
    releaseDocument(sourceViewer.getDocument)
  }

  private def manageDocument(document: IDocument): Unit = {
    if (document != null) {
      sourceViewer.getDocument.addPositionCategory(positionCategory)
      sourceViewer.getDocument.addPositionUpdater(positionUpdater)
      sourceViewer.getDocument.addDocumentListener(documentChangeListener)
    }
  }

  private def releaseDocument(document: IDocument): Unit = {
    if (document != null) {
      document.removePositionUpdater(positionUpdater)
      document.removeDocumentListener(documentChangeListener)
    }
  }

  /** Compute the list of positions that have to be added and removed from the document's model.
    * It does so by comparing the passed `newPositions` and the existing positions held by the
    * `sourceViewer`'s document.
    *
    * @param `newPositions` The freshly computed positions that should be semantically highlighted in the editor.
    * @return A data object holding the sequence of positions that should be added and removed from this `sourceViewer`'s document.
    */
  def createDocumentPositionsChange(newPositions: List[Position]): Option[DocumentPositionsChange] = {
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
                } else positionsToAdd += newPos
              }
          }
        }
      }

      DocumentPositionsChange(positionsToAdd.toList, positionsToRemove.toList)
    }
  }

  /** Uses the passed `positionsChange` to update the `sourceViewer`'s document. */
  def updateDocumentPositions(positionsChange: DocumentPositionsChange): Unit = {
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
}

private object DocumentProxy {

  class DefaultTextInputListener(presenter: DocumentProxy, reconciler: Job) extends ITextInputListener {
    override def inputDocumentAboutToBeChanged(oldInput: IDocument, newInput: IDocument): Unit = {
      reconciler.cancel()
      presenter.releaseDocument(oldInput)
    }
    override def inputDocumentChanged(oldInput: IDocument, newInput: IDocument): Unit = presenter.manageDocument(newInput)
  }

  class DefaultDocumentListener(reconciler: Job) extends IDocumentListener {
    override def documentAboutToBeChanged(event: DocumentEvent): Unit = reconciler.cancel()
    override def documentChanged(event: DocumentEvent): Unit = {}
  }

  /** Every time a change is applied to the document (model) hold by the `sourceViewer`, updates all positions categorized
    * under the passed `category`. It works by side-effecting, as needed, all positions stored under `category` in the document
    * model.
    *
    * @param sourceViewer Used to retrieve the document to which this listener is attached to.
    * @param category     Used to retrieve the document's positions this listener is interested in updating.
    */
  class HighlightedPositionUpdater(sourceViewer: ISourceViewer, category: String) extends IPositionUpdater with HasLogger {

    override def update(event: DocumentEvent): Unit = withDocument(sourceViewer) {
      doUpdate(_, event)
    }

    private def doUpdate(document: IDocument, event: DocumentEvent): Unit = {
      val editionOffset = event.getOffset
      val editionLength = event.getLength
      val editionEnd = editionOffset + editionLength
      val newText = event.getText()
      val newLength = Option(newText) map (_.length) getOrElse 0

      val positions = {
        try document.getPositions(category)
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
        } else {
          // The edition affected the current `position`, hence let's delete it.
          // Ideally, we would like to remove the position from the `document` via `document.removePosition(category, position)`. 
          // However, this is not safe because in the UI-thread we could be concurrently calling `document.addPosition` for the 
          // current `position` (with some unlucky timing). Therefore, we only side-effect the `position`'s attributes, and let 
          // code that is running in the UI-thread taking care of physically removing the deleted positions from the document's model.
          position.delete()
        }
      }
    }
  }
}