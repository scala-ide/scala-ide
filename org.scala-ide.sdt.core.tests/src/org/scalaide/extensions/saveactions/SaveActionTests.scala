package org.scalaide.extensions
package saveactions

import org.eclipse.jface.text.{Document => EDocument}
import org.scalaide.core.internal.text.TextDocument
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.core.ui.TextEditTests
import org.scalaide.extensions.SaveAction

abstract class SaveActionTests extends TextEditTests {

  def saveAction: SaveAction

  var document: Document = _

  private var udoc: EDocument = _

  override def runTest(source: String, operation: Operation) = {
    udoc = new EDocument(source)
    document = new TextDocument(udoc)
    operation.execute()
  }

  override def source = udoc.get()

  case object SaveEvent extends Operation {
    override def execute() = {
      val changes = saveAction.perform()
      val sorted = changes.sortBy {
        case TextChange(start, _, _) => -start
      }
      sorted foreach {
        case TextChange(start, end, text) =>
          udoc.replace(start, end-start, text)
      }
    }
  }

}
