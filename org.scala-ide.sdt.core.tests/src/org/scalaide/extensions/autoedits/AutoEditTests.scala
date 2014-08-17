package org.scalaide.extensions
package autoedits

import org.eclipse.jface.text.{Document => EDocument}
import org.scalaide.core.internal.text.TextDocument
import org.scalaide.core.text.CursorUpdate
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.core.ui.TextEditTests

abstract class AutoEditTests extends TextEditTests {

  def autoEdit(doc: Document, change: TextChange): AutoEdit

  private var udoc: EDocument = _

  override def runTest(source: String, operation: Operation) = {
    udoc = new EDocument(source)
    operation.execute()
  }

  override def source = udoc.get()

  case class Add(text: String) extends Operation {
    override def execute() = {
      val change = TextChange(caretOffset, caretOffset, text)
      val appliedChange = autoEdit(new TextDocument(udoc), change).perform()

      appliedChange foreach {
        case TextChange(start, end, text) =>
          udoc.replace(start, end-start, text)

        case CursorUpdate(TextChange(start, end, text), cursorPos) =>
          udoc.replace(start, end-start, text)
          caretOffset = cursorPos

        case o =>
          throw new AssertionError(s"Invalid change object '$o'.")
      }
    }
  }
}
