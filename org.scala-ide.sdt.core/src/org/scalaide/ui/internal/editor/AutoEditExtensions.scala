package org.scalaide.ui.internal.editor

import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.swt.events.VerifyEvent
import org.scalaide.core.internal.text.TextDocument
import org.scalaide.core.text.Add
import org.scalaide.core.text.Change
import org.scalaide.core.text.Document
import org.scalaide.core.text.Remove
import org.scalaide.core.text.TextChange
import org.scalaide.extensions.AutoEdit
import org.scalaide.extensions.AutoEditSetting
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.eclipse.EclipseUtils

object AutoEditExtensions {

  val autoEditSettings: Seq[AutoEditSetting] = Seq(
  )
}

trait AutoEditExtensions extends HasLogger {

  def sourceViewer: ISourceViewer

  def handleVerifyEvent(event: VerifyEvent, modelRange: IRegion): Unit = {
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
    }
  }

  private def applyAutoEdit(doc: Document, change: TextChange): Option[Change] = {
    val exts = Seq[(Document, TextChange) => AutoEdit](
    )
    val iter = exts.iterator map { ext =>
      val instance = ext(doc, change)
      performExtension(instance)
    }
    val appliedAutoEdit = iter.find(_.exists(_ != change))
    appliedAutoEdit.flatten
  }

  private def performExtension(instance: AutoEdit): Option[Change] = {
    val id = instance.setting.id
    EclipseUtils.withSafeRunner(s"An error occurred while executing auto edit '$id'.") {
      instance.perform()
    }
  }
}
