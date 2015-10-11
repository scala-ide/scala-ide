package org.scalaide.ui.internal.editor

import org.eclipse.jdt.internal.ui.text.SmartBackspaceManager
import org.eclipse.jdt.internal.ui.text.SmartBackspaceManager.UndoSpec
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.TextUtilities
import org.eclipse.jface.text.link.LinkedModeModel
import org.eclipse.jface.text.link.LinkedModeUI
import org.eclipse.jface.text.link.LinkedPosition
import org.eclipse.jface.text.link.LinkedPositionGroup
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.text.edits.DeleteEdit
import org.eclipse.text.edits.ReplaceEdit
import org.eclipse.ui.texteditor.link.EditorLinkedModeUI
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.internal.extensions.AutoEdits
import org.scalaide.core.internal.extensions.ExtensionCompiler
import org.scalaide.core.internal.extensions.ExtensionCreators
import org.scalaide.core.internal.statistics.Features.Feature
import org.scalaide.core.internal.statistics.Groups
import org.scalaide.core.internal.text.TextDocument
import org.scalaide.core.text.Change
import org.scalaide.core.text.CursorUpdate
import org.scalaide.core.text.LinkedModel
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.AutoEdit
import org.scalaide.logging.HasLogger
import org.scalaide.util.eclipse.EclipseUtils

object AutoEditExtensions extends AnyRef with HasLogger {

  /**
   * Contains all available auto edits. They are cached here because their
   * creation is expensive.
   */
  private val autoEdits = {
    AutoEdits.autoEditData flatMap {
      case (fqn, setting) ⇒
        ExtensionCompiler.savelyLoadExtension[ExtensionCreators.AutoEdit](fqn).map(setting → _)
    }
  }

}

/**
 * Maintains the logic that is needed to handle auto edits correctly.
 */
trait AutoEditExtensions extends HasLogger {
  import AutoEditExtensions._

  private type Handler = PartialFunction[Change, Unit]

  /**
   * The source viewer of the editor, to which the auto edits should be applied
   * to.
   */
  def sourceViewer: ISourceViewer

  /**
   * The document partitioning of the editor, to which the auto edits should be
   * applied to.
   */
  def documentPartitioning: String

  /**
   * Needs to update the text viewer of the editor to which the auto edits are
   * applied to. This method is called by [[applyVerifyEvent]] after an auto
   * edit is applied to the underlying document.
   *
   * `cursorPos` is the new position of the cursor that should be shown to
   * users after.
   */
  def updateTextViewer(cursorPos: Int): Unit

  /**
   * Provides a smart backspace manager that is wired to the editor, to which
   * the auto edits should be applied to.
   */
  def smartBackspaceManager: SmartBackspaceManager

  /**
   * Handles `event` by giving its information to the auto edits. `event.doit`
   * is set to `false` if a auto edit could be found that can be applied and if
   * no errors occurred.
   */
  def applyVerifyEvent(event: VerifyEvent, modelRange: IRegion): Unit = {
    val udoc = sourceViewer.getDocument()
    val start = modelRange.getOffset()
    val end = start+modelRange.getLength()
    val text = event.text
    val change = TextChange(start, end, text)

    val handleTextChange: Handler = {
      case TextChange(start, end, text) =>
        udoc.replace(start, end-start, text)
        event.doit = false
        event.text = text
    }

    val handleCursorUpdate: Handler = {
      case CursorUpdate(autoEdit, cursorPos, smartBackspaceEnabled) =>
        handleTextChange(autoEdit)
        if (smartBackspaceEnabled)
          registerSmartBackspace(cursorPos, change.start, autoEdit)
        updateTextViewer(cursorPos)
    }

    val handleLinkedModel: Handler = {
      case LinkedModel(autoEdit, exitPosition, positionGroups) =>
        handleTextChange(autoEdit)
        val ui = mkEditorLinkedMode(mkLinkedModeModel(udoc, positionGroups), exitPosition)
        ui.enter()
    }

    val handlers = Seq(handleTextChange, handleCursorUpdate, handleLinkedModel)

    performAutoEdits(udoc, change, handlers)
  }

  /** Creates a linked model with given `positionGroups`. */
  private def mkLinkedModeModel(doc: IDocument, positionGroups: Seq[Seq[(Int, Int)]]): LinkedModeModel = {
    val model = new LinkedModeModel {
      positionGroups foreach { ps =>
        this addGroup new LinkedPositionGroup {
          ps foreach { case (off, len) =>
            this addPosition new LinkedPosition(doc, off, len, 0)
          }
        }
      }
    }

    model.forceInstall()
    model
  }

  /** Creates a linked mode with a given `exitPosition`. */
  private def mkEditorLinkedMode(model: LinkedModeModel, exitPosition: Int): EditorLinkedModeUI = {
    val ui = new EditorLinkedModeUI(model, sourceViewer)

    ui.setCyclingMode(LinkedModeUI.CYCLE_WHEN_NO_PARENT)
    ui.setExitPosition(sourceViewer, exitPosition, 0, Integer.MAX_VALUE)
    ui
  }

  /**
   * A smart backspace is a feature that allows to undo an auto edit by pressing
   * the backspace key. This method registers such a smart backspace in the
   * editor when the `originalPos` and the offset of the `autoEdit` are
   * different.
   *
   * @param triggerOffset
   *        the position of the cursor where this feature is activated in the
   *        case the backspace key is pressed.
   * @param originalPos
   *        the position where the text change should happen once the backspace
   *        is pressed.
   * @param autoEdit
   *        the text change that should be undone
   */
  private def registerSmartBackspace(triggerOffset: Int, originalPos: Int, autoEdit: TextChange): Unit = {
    if (originalPos != autoEdit.start) {
      val textLen = autoEdit.text.length()
      val deleteAtTrigger = new DeleteEdit(triggerOffset-textLen, textLen)
      val insertAtOldPos = new ReplaceEdit(originalPos, 0, autoEdit.text)
      val selectionAfterUndo = new Region(originalPos+textLen, 0)
      val undo = new UndoSpec(triggerOffset, selectionAfterUndo, Array(deleteAtTrigger, insertAtOldPos), 0, null)
      smartBackspaceManager.register(undo)
    }
  }

  /**
   * Tries to find an auto edit that produces a `Change` object, which is
   * applied afterwards.
   *
   * `udoc` and `change` are used to create the auto edits, `handlers` takes
   * the produced `Change` object.
   */
  private def performAutoEdits(udoc: IDocument, change: TextChange, handlers: Seq[Handler]): Unit = {
    val partition = TextUtilities.getPartition(
        udoc, documentPartitioning,
        change.start, /* preferOpenPartitions */ true).getType()

    def configuredForPartition(partitions: Set[String]) =
      partitions.isEmpty || partitions(partition)

    val iter =
      for {
        (setting, ext) <- autoEdits.iterator
        if isEnabled(setting.id) && configuredForPartition(setting.partitions)
      } yield {
        val doc = new TextDocument(udoc)
        val instance = ext(doc, change)
        val appliedChange = performExtension(instance)
        appliedChange map { c => applyChange(c, instance, handlers) }
      }

    iter find (_.isDefined)
  }

  /**
   * Applies `change` to `handlers`. `autoEdit` is the auto edit that produced
   * the object passed to `change`.
   *
   * Returns `None` if `change` could not be applied.
   */
  private def applyChange(change: Change, autoEdit: AutoEdit, handlers: Seq[Handler]): Option[Unit] = {
    val id = autoEdit.setting.id

    val feature = Feature(id)(autoEdit.setting.name, Groups.AutoEdit)
    feature.incUsageCounter()

    handlers find (_ isDefinedAt change) flatMap { handler =>
      EclipseUtils.withSafeRunner(s"An error occurred while applying changes of auto edit '$id'.") {
        handler(change)
      }
    }
  }

  /**
   * Performs an auto edit and returns it produced `Change` object. In case the
   * result is invalid or no result is produced `None` is returned.
   */
  private def performExtension(instance: AutoEdit): Option[Change] = {
    val id = instance.setting.id

    if (isEnabled(id))
      EclipseUtils.withSafeRunner(s"An error occurred while executing auto edit '$id'.") {
        instance.perform() match {
          case None                                                       => None
          case o @ Some(_: TextChange | _: CursorUpdate | _: LinkedModel) => o
          case Some(o) =>
            eclipseLog.warn(s"The returned object '$o' of auto edit '$id' is invalid.")
            None
        }
      }.flatten
    else
      None
  }

  /** Checks if an auto edit given by its `id` is enabled. */
  private def isEnabled(id: String): Boolean =
    IScalaPlugin().getPreferenceStore().getBoolean(id)
}
