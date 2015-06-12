package org.scalaide.extensions
package saveactions

import org.junit.Test
import org.scalaide.core.text.Document

class TabToSpaceConverterTest extends DocumentSaveActionTests {

  override def saveAction(doc: Document) = new TabToSpaceConverter {
    override val document = doc
  }

  @Test
  def convert_tabs_to_spaces() = """^
    class X {
      val x = 0
    \tdef f = {
        val value = 0
    \t\tval z\t\t = 0
      }
    }""".replaceAll("\\\\t", "\t") becomes """^
    class X {
      val x = 0
      def f = {
        val value = 0
        val z     = 0
      }
    }""" after SaveEvent
}
