package org.scalaide.extensions.autoedits

import org.junit.Test
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange

class ConvertToUnicodeTest extends AutoEditTests {

  override def autoEdit(doc: Document, tc: TextChange) = new ConvertToUnicode {
    override val document = doc
    override val textChange = tc
  }

  @Test
  def convert_to_←() = """
    for (i <^
    """ becomes """
    for (i ←^
    """ after Add("-")

  @Test
  def convert_to_→() = """
    a -^ b
    """ becomes """
    a →^ b
    """ after Add(">")

  @Test
  def convert_to_⇒() = """
    a =^ b
    """ becomes """
    a ⇒^ b
    """ after Add(">")

  @Test
  def convert_on_paste_to_←() = """
    for (i ^
    """ becomes """
    for (i ←^
    """ after Add("<-")

  @Test
  def convert_on_paste_to_→() = """
    a ^ b
    """ becomes """
    a →^ b
    """ after Add("->")

  @Test
  def convert_on_paste_to_⇒() = """
    a ^ b
    """ becomes """
    a ⇒^ b
    """ after Add("=>")

  @Test
  def handle_empty_file() =
    "^" becomes "-^" after Add("-")
}
