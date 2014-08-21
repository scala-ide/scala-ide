package org.scalaide.ui.internal.editor

import org.eclipse.jdt.internal.ui.text.SmartBackspaceManager
import org.eclipse.jdt.internal.ui.text.SmartBackspaceManager.UndoSpec
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IDocumentExtension3
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.TextUtilities
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.swt.events.VerifyEvent
import org.eclipse.text.edits.DeleteEdit
import org.eclipse.text.edits.ReplaceEdit
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.extensions.autoedits.CloseCurlyBraceCreator
import org.scalaide.core.internal.extensions.autoedits.ConvertToUnicodeCreator
import org.scalaide.core.internal.extensions.autoedits.JumpOverClosingCurlyBraceCreator
import org.scalaide.core.internal.extensions.autoedits.SmartSemicolonInsertionCreator
import org.scalaide.core.internal.text.TextDocument
import org.scalaide.core.text.Add
import org.scalaide.core.text.Change
import org.scalaide.core.text.CursorUpdate
import org.scalaide.core.text.Remove
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.AutoEdit
import org.scalaide.extensions.AutoEditSetting
import org.scalaide.extensions.autoedits.CloseCurlyBraceSetting
import org.scalaide.extensions.autoedits.ConvertToUnicodeSetting
import org.scalaide.extensions.autoedits.JumpOverClosingCurlyBraceSetting
import org.scalaide.extensions.autoedits.SmartSemicolonInsertionSetting
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.eclipse.EclipseUtils

object AutoEditExtensions {

  val autoEditSettings: Seq[AutoEditSetting] = Seq(
    ConvertToUnicodeSetting,
    SmartSemicolonInsertionSetting,
    CloseCurlyBraceSetting,
    JumpOverClosingCurlyBraceSetting
  )

  private val autoEdits = Seq(
    ConvertToUnicodeSetting -> ConvertToUnicodeCreator.create _,
    SmartSemicolonInsertionSetting -> SmartSemicolonInsertionCreator.create _,
    CloseCurlyBraceSetting -> CloseCurlyBraceCreator.create _,
    JumpOverClosingCurlyBraceSetting -> JumpOverClosingCurlyBraceCreator.create _
  )
}

trait AutoEditExtensions extends HasLogger {
  import AutoEditExtensions._

  def sourceViewer: ISourceViewer

  def updateTextViewer(cursorPos: Int): Unit

  def smartBackspaceManager: SmartBackspaceManager

  def applyVerifyEvent(event: VerifyEvent, modelRange: IRegion): Unit = {
    val udoc = sourceViewer.getDocument()
    val start = modelRange.getOffset()
    val end = start+modelRange.getLength()
    val text = event.text
    val change =
      if (text.isEmpty()) Remove(start, end)
      else Add(start, text)
    val res = applyAutoEdit(udoc, change)

    res foreach {
      case TextChange(start, end, text) =>
        udoc.replace(start, end-start, text)
        event.doit = false
        event.text = text

      case CursorUpdate(autoEdit @ TextChange(start, end, text), cursorPos, smartBackspaceEnabled) =>
        udoc.replace(start, end-start, text)
        event.doit = false
        event.text = text
        if (smartBackspaceEnabled)
          registerSmartBackspace(cursorPos, change.start, autoEdit)
        updateTextViewer(cursorPos)
    }
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

  private def applyAutoEdit(udoc: IDocument, change: TextChange): Option[Change] = {
    val partition = TextUtilities.getPartition(
        udoc, IDocumentExtension3.DEFAULT_PARTITIONING,
        change.start, /* preferOpenPartitions */ true)

    def configuredForPartition(partitions: Seq[String]) =
      partitions.isEmpty || partitions.contains(partition)

    val iter = for ((setting, ext) <- autoEdits.iterator
        if isEnabled(setting.id) && configuredForPartition(setting.partitions)) yield {
      val doc = new TextDocument(udoc)
      val instance = ext(doc, change)
      performExtension(instance)
    }

    val appliedAutoEdit = iter find (_.isDefined)
    appliedAutoEdit.flatten
  }

  private def performExtension(instance: AutoEdit): Option[Change] = {
    val id = instance.setting.id

    if (isEnabled(id))
      EclipseUtils.withSafeRunner(s"An error occurred while executing auto edit '$id'.") {
        instance.perform() match {
          case None                                      => None
          case o @ Some(_: TextChange | _: CursorUpdate) => o
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
    ScalaPlugin.prefStore.getBoolean(id)
}
