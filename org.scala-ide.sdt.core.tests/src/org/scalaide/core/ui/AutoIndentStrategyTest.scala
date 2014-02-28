package org.scalaide.core.ui

import org.junit.Before
import org.junit.Test
import org.scalaide.core.internal.formatter.FormatterPreferences._
import org.scalaide.ui.internal.editor.autoedits.AutoIndentStrategy
import org.scalaide.ui.internal.preferences.EditorPreferencePage._

import AutoEditStrategyTests._
import scalariform.formatter.preferences._

class AutoIndentStrategyTest extends AutoEditStrategyTests(new AutoIndentStrategy(prefStore)) {

  implicit class ToCorrectTestInput(s: String) {
    def c = s.replaceAll("""\\t""", "\t")
  }

  val tab = Add("\t")

  @Before
  def startup(): Unit = {
    enable(P_ENABLE_AUTO_INDENT_ON_TAB, true)
    enable(IndentWithTabs.eclipseKey, false)
    setIntPref(IndentSpaces.eclipseKey, 2)
  }

  @Test
  def no_extra_indent_on_tab_when_feature_not_enabled() = disabled(P_ENABLE_AUTO_INDENT_ON_TAB) { """
    class X {
      def f = {
        val x = 0
    ^
      }
    }
    """ becomes """
    class X {
      def f = {
        val x = 0
      ^
      }
    }
    """ after tab
  }

  @Test
  def indent_with_tab_when_feature_not_enabled() = disabled(P_ENABLE_AUTO_INDENT_ON_TAB) { enabled(IndentWithTabs.eclipseKey) { """
    class X {
    ^
    }
    """ becomes """
    class X {
    \t^
    }
    """.c after tab
  }}

  @Test
  def insert_tab_when_corresponding_preference_enabled() = enabled(IndentWithTabs.eclipseKey) { """
    class X {
      def f = {
        val x = 0
    ^
      }
    }
    """ becomes """
    class X {
      def f = {
        val x = 0
    \t\t^
      }
    }
    """.c after tab
  }

  @Test
  def normal_indent_when_indent_depth_of_current_line_is_equal_to_previous_line() = """
    class X {
    ^
    }
    """ becomes """
    class X {
      ^
    }
    """ after tab

  @Test
  def normal_indent_when_indent_depth_of_previous_line_is_lower_than_current_line() = """
    class X {
      val x = 0
        ^
    }
    """ becomes """
    class X {
      val x = 0
          ^
    }
    """ after tab

  @Test
  def indent_by_same_depth_as_previous_line() = """
    class X {
      def f = {
        val x = 0
    ^
      }
    }
    """ becomes """
    class X {
      def f = {
        val x = 0
        ^
      }
    }
    """ after tab

  @Test
  def indent_by_same_depth_as_previous_line_when_current_line_contains_whitespace() = """
    class X {
      def f = {
        val x = 0
    ^    $
      }
    }
    """ becomes """
    class X {
      def f = {
        val x = 0
        ^    $
      }
    }
    """ after tab

  @Test
  def indent_by_same_depth_as_previous_non_whitespace_line() = """
    class X {
      def f = {
        val x = 0
        $

    ^
      }
    }
    """ becomes """
    class X {
      def f = {
        val x = 0
        $

        ^
      }
    }
    """ after tab

  @Test
  def indent_by_same_depth_when_previous_line_contains_tabs() = """
    class X {
    \tdef f = {
    \t\tval x = 0
    ^
    \t}
    }
    """.c becomes """
    class X {
    \tdef f = {
    \t\tval x = 0
    \t\t^
    \t}
    }
    """.c after tab

  @Test
  def normal_tab_indent_when_current_line_contains_non_whitespace_text_before_cursor() = """
    class X {
      def f = {
                val x = 0
    val y = 0^
      }
    }
    """ becomes """
    class X {
      def f = {
                val x = 0
    val y = 0  ^
      }
    }
    """ after tab

  @Test
  def normal_tab_indent_when_current_line_contains_non_whitespace_text_after_cursor() = """
    class X {
      def f = {
        val x = 0
    ^     // comment
      }
    }
    """ becomes """
    class X {
      def f = {
        val x = 0
      ^     // comment
      }
    }
    """ after tab
}
