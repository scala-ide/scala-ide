package org.scalaide.ui.internal.editor

import org.eclipse.jface.text.TextViewer
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.swt.events.VerifyEvent
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.extensions.autoedits.ConvertToUnicodeCreator
import org.scalaide.core.internal.text.TextDocument
import org.scalaide.core.text.Add
import org.scalaide.core.text.Change
import org.scalaide.core.text.CursorUpdate
import org.scalaide.core.text.Document
import org.scalaide.core.text.Remove
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.AutoEdit
import org.scalaide.extensions.AutoEditSetting
import org.scalaide.extensions.autoedits.ConvertToUnicodeSetting
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.eclipse.EclipseUtils

object AutoEditExtensions {

  val autoEditSettings: Seq[AutoEditSetting] = Seq(
    ConvertToUnicodeSetting
  )
}

trait AutoEditExtensions extends HasLogger { self: TextViewer =>

  def sourceViewer: ISourceViewer

  def applyVerifyEvent(event: VerifyEvent): Unit = {
    val modelRange = event2ModelRange(event)
    val udoc = sourceViewer.getDocument()
    val start = modelRange.getOffset()
    val end = start+modelRange.getLength()
    val text = event.text
    val change =
      if (text.isEmpty()) Remove(start, end)
      else Add(start, text)
    val doc = new TextDocument(udoc)
    val res = applyAutoEdit(doc, change)

    res foreach {
      case TextChange(start, end, text) =>
        udoc.replace(start, end-start, text)
        event.doit = false
        event.text = text

      case CursorUpdate(TextChange(start, end, text), cursorPos) =>
        udoc.replace(start, end-start, text)
        event.doit = false
        event.text = text
        val widgetCaret = modelOffset2WidgetOffset(cursorPos)
        self.setSelectedRange(widgetCaret, 0)
    }
  }

  private def applyAutoEdit(doc: Document, change: TextChange): Option[Change] = {
    val exts = Seq[(Document, TextChange) => AutoEdit](
      ConvertToUnicodeCreator.create _
    )
    val iter = exts.iterator map { ext =>
      val instance = ext(doc, change)
      performExtension(instance)
    }
    val appliedAutoEdit = iter find (_.isDefined)
    appliedAutoEdit.flatten
  }

  private def performExtension(instance: AutoEdit): Option[Change] = {
    def isEnabled(id: String): Boolean =
      ScalaPlugin.prefStore.getBoolean(id)

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
}
