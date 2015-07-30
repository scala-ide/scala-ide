package org.scalaide.extensions
package autoedits

import org.junit.Test
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange

class SurroundBlockTest extends AutoEditTests {

  override def autoEdit(doc: Document, change: TextChange) = new SurroundBlock {
    override val document = doc
    override val textChange = change
  }

  val curlyBrace = Add("{")

  @Test
  def add_ending_on_smaller_indent() = """
    class X {
      def f = ^
        if (true)
          1
        else
          0
    }
    """ becomes """
    class X {
      def f = {^
        if (true)
          1
        else
          0
      }
    }
    """ after curlyBrace

  @Test
  def add_ending_on_indent_of_same_size() = """
    class X {
      def f = ^
        0
      def g =
        0
    }
    """ becomes """
    class X {
      def f = {^
        0
      }
      def g =
        0
    }
    """ after curlyBrace

  @Test
  def add_ending_when_trailing_whitespace_exists() = """
    class X {
      def f = ^    $
        0    $
    }
    """ becomes """
    class X {
      def f = {^    $
        0    $
      }
    }
    """ after curlyBrace

  @Test
  def add_no_ending_if_caret_not_at_end_of_line() = """
    class X {
      List(1) map^ (_+1)
    }
    """ becomes """
    class X {
      List(1) map{^ (_+1)
    }
    """ after curlyBrace

  @Test
  def add_no_ending_if_it_already_exists() = """
    class X {
      def f = ^
        0
      }
    }
    """ becomes """
    class X {
      def f = {^
        0
      }
    }
    """ after curlyBrace

  @Test
  def add_no_ending_if_next_line_has_same_indent() = """
    class X {
      val a = ^
      val b = 0
    }
    """ becomes """
    class X {
      val a = {^
      val b = 0
    }
    """ after curlyBrace

  @Test
  def add_no_ending_if_next_line_is_empty() = """
    class X {
      val a = ^

      val b = 0
    }
    """ becomes """
    class X {
      val a = {^

      val b = 0
    }
    """ after curlyBrace

  @Test
  def add_no_ending_at_end_of_document() = """
    def f = ^
      0""" becomes """
    def f = {^
      0""" after curlyBrace

  @Test
  def add_no_ending_in_the_middle_of_a_block() = """
    class X {
      def f(i: Int) = i
      def g = ^
        f {

          0
        }
      def h = 0
    }
    """ becomes """
    class X {
      def f(i: Int) = i
      def g = {^
        f {

          0
        }
      }
      def h = 0
    }
    """ after curlyBrace

  @Test
  def add_no_ending_if_next_line_is_rbrace() = """
    class X {
      val a = ^
    }
    """ becomes """
    class X {
      val a = {^
    }
    """ after curlyBrace

  @Test
  def add_no_rbrace_on_the_same_level() = """
    object X {

      def g(i: Int) = i

      def f = {
        g(0) ^
        g(0)
      }
    }
    """ becomes """
    object X {

      def g(i: Int) = i

      def f = {
        g(0) {^
        g(0)
      }
    }
    """ after curlyBrace

  @Test
  def surround_block_before_ident() = """
    object X {
      def f = {
        val x = ^
          1
        x
      }
    }
    """ becomes """
    object X {
      def f = {
        val x = {^
          1
        }
        x
      }
    }
    """ after curlyBrace

  @Test
  def surround_block_before_empty_line() = """
    object X {
      def f = {
        val x = ^
          1

        x
      }
    }
    """ becomes """
    object X {
      def f = {
        val x = {^
          1
        }

        x
      }
    }
    """ after curlyBrace

  @Test
  def add_no_brace_before_empty_line_with_comments() = """
    test("") ^

    // This tests whether file input stream remembers what files were seen before
    // the master failure and uses them again to process a large window operation.
    test("recovery with file input stream") {
    """ becomes """
    test("") {^

    // This tests whether file input stream remembers what files were seen before
    // the master failure and uses them again to process a large window operation.
    test("recovery with file input stream") {
    """ after curlyBrace

  @Test
  def add_brace_inline_else() = """
    if (x > 0) ^
      true
    else
      false
    """ becomes """
    if (x > 0) {^
      true
    } else
      false
    """ after curlyBrace

  @Test
  def add_brace_inline_catch() = """
    try ^
      throw null
    catch {
      case _ => ()
    }
    """ becomes """
    try {^
      throw null
    } catch {
      case _ => ()
    }
    """ after curlyBrace

  @Test
  def add_brace_inline_finally() = """
    try ^
      throw null
    finally {
      ()
    }
    """ becomes """
    try {^
      throw null
    } finally {
      ()
    }
    """ after curlyBrace
}
