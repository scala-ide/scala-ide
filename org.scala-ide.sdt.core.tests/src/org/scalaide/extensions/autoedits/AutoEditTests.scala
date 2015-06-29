package org.scalaide.extensions
package autoedits

import org.eclipse.jface.text.{Document => EDocument}
import org.scalaide.core.internal.text.TextDocument
import org.scalaide.core.{text => impl}
import org.scalaide.core.text.CursorUpdate
import org.scalaide.core.text.Document
import org.scalaide.core.text.LinkedModel
import org.scalaide.core.text.TextChange
import org.scalaide.core.ui.TextEditTests
import org.scalaide.extensions.AutoEdit

abstract class AutoEditTests extends TextEditTests {

  def autoEdit(doc: Document, change: TextChange): AutoEdit

  private var udoc: EDocument = _

  override def runTest(source: String, operation: Operation) = {
    udoc = new EDocument(source)
    operation.execute()
  }

  override def source = udoc.get()

  abstract class AutoEditOperation extends Operation {
    def applyChange(change: TextChange): Unit = {
      val appliedChange = autoEdit(new TextDocument(udoc), change).perform()

      appliedChange match {
        case Some(TextChange(start, end, text)) =>
          udoc.replace(start, end-start, text)

        case Some(CursorUpdate(TextChange(start, end, text), cursorPos, _)) =>
          udoc.replace(start, end-start, text)
          caretOffset = cursorPos

        case Some(LinkedModel(TextChange(start, end, text), exitPosition, positionGroups)) =>
          udoc.replace(start, end-start, text)
          val groups = positionGroups.flatten
          caretOffset = applyLinkedModel(udoc, exitPosition, groups)

        case Some(o) =>
          throw new AssertionError(s"Invalid change object '$o'.")

        case None =>
          udoc.replace(change.start, change.end-change.start, change.text)
          change match {
            case impl.Add(_, text) =>
              caretOffset += text.size
            case impl.Remove(start, end) =>
              caretOffset += start-end
          }
      }
    }
  }

  case class Add(text: String) extends AutoEditOperation {
    override def execute() = {
      val change = impl.Add(caretOffset, text)
      applyChange(change)
    }
  }

  case class Remove(text: String) extends AutoEditOperation {
    override def execute() = {
      val actual = udoc.get().substring(caretOffset-text.length(), caretOffset)
      require(text == actual, "removeable content does not equal to the declared content")

      val change = impl.Remove(caretOffset-text.length(), caretOffset)
      applyChange(change)
    }
  }

  case class Replace(start: Int, end: Int, text: String) extends AutoEditOperation {
    override def execute() = {
      val change = impl.Replace(start, end, text)
      applyChange(change)
    }
  }
}
