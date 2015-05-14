package org.scalaide.core.ui

import org.eclipse.jdt.ui.text.IJavaPartitions
import org.junit.Before
import org.junit.Test
import org.scalaide.ui.internal.editor.autoedits.CommentAutoIndentStrategy
import org.scalaide.ui.internal.preferences.EditorPreferencePage._

class CommentAutoEditStrategyTest extends AutoEditStrategyTests {

  val strategy = new CommentAutoIndentStrategy(prefStore, IJavaPartitions.JAVA_PARTITIONING)

  val newline = Add("\n")

  @Before
  def startUp(): Unit = {
    import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants._

    enable(P_ENABLE_AUTO_CLOSING_COMMENTS, true)
    enable(P_ENABLE_AUTO_BREAKING_COMMENTS, true)
    setIntPref(EDITOR_PRINT_MARGIN_COLUMN, 20)
  }

  @Test
  def no_close_on_deactiveted_feature(): Unit = {
    enable(P_ENABLE_AUTO_CLOSING_COMMENTS, false)
    """
    /**^
    """ becomes
    """
    /**
     * ^
    """ after newline
  }

  @Test
  def openDocComment_topLevel(): Unit = {
    """
    /**^
    class Foo
    """ becomes
    """
    /**
     * ^
     */
    class Foo
    """ after newline
  }

  @Test
  def openMultilineComment_topLevel(): Unit = {
    """
    /*^
    class Foo
    """ becomes
    """
    /*
     * ^
     */
    class Foo
    """ after newline
  }

  @Test
  def openDocComment_topLevel_with_nested(): Unit = {
    """
    /**^
    class Foo {
      /** blah */
      def foo(): Unit = {}
    }
    """ becomes
    """
    /**
     * ^
     */
    class Foo {
      /** blah */
      def foo(): Unit = {}
    }
    """ after newline
  }

  @Test
  def openDocComment_topLevel_with_stringLit(): Unit = {
    """
    /**^
    class Foo {
       def foo(): Unit = {
            "/* */" // tricky, this trips the Java auto-edit :-D
      }
    }
    """ becomes
    """
    /**
     * ^
     */
    class Foo {
       def foo(): Unit = {
            "/* */" // tricky, this trips the Java auto-edit :-D
      }
    }
    """ after newline
  }

  @Test
  def openDocComment_nested(): Unit = {
    """
    /** blah */
    class Foo {
      /**^
      def foo(): Unit = {
      }
    }
    """ becomes
    """
    /** blah */
    class Foo {
      /**
       * ^
       */
      def foo(): Unit = {
      }
    }
    """ after newline
  }

  @Test
  def openDocComment_nested_with_other_docs(): Unit = {
    """
    /** blah */
    class Foo {
      /**^
      def foo(): Unit = {
      }
      /** */
      def bar: Unit
    }
    """ becomes
    """
    /** blah */
    class Foo {
      /**
       * ^
       */
      def foo(): Unit = {
      }
      /** */
      def bar: Unit
    }
    """ after newline
  }

  @Test
  def closedDocComment_topLevel(): Unit = {
    """
    /** ^blah */
    class Foo {
    }
    """ becomes
    """
    /** $
     *  ^blah */
    class Foo {
    }
    """ after newline
  }

  @Test
  def closedMultilineComment_topLevel(): Unit = {
    """
    /*  ^blah */
    class Foo {
    }
    """ becomes
    """
    /*  $
     *  ^blah */
    class Foo {
    }
    """ after newline
  }

  @Test
  def closedDocComment_topLevel_nested(): Unit = {
    """
    /** blah */
    class Foo {
      /**^*/
      def foo(): Unit = {
      }
      /** */
      def bar: Unit
    }
    """ becomes
    """
    /** blah */
    class Foo {
      /**
       * ^*/
      def foo(): Unit = {
      }
      /** */
      def bar: Unit
    }
    """ after newline
  }

  @Test
  def openDocComment_at_end(): Unit = {
    """
    class Foo {
    }/**^
    """ becomes
    """
    class Foo {
    }/**
    * ^
    */
    """ after newline
  }

  @Test
  def closedDocComment_no_asterisk_on_empty_line(): Unit = {
    """
    /**
    ^
    */
    class Foo {
    }
    """  becomes
    """
    /**
    $
    ^
    */
    class Foo {
    }
    """ after newline
  }

  @Test
  def closedMultilineComment_no_asterisk_on_empty_line(): Unit = {
    """
    /*
    ^
    */
    class Foo {
    }
    """ becomes
    """
    /*
    $
    ^
    */
    class Foo {
    }
    """ after newline
  }

  @Test
  def closedDocComment_no_asterisk_on_line_not_starting_with_asterisk(): Unit = {
    """
    /**
    hello^
    */
    class Foo {
    }
    """ becomes
    """
    /**
    hello
    ^
    */
    class Foo {
    }
    """ after newline
  }

  @Test
  def closedDocComment_line_break(): Unit = {
    """
    /** one^two
     */
    class Foo {
    }
    """ becomes
    """
    /** one
     *  ^two
     */
    class Foo {
    }
    """ after newline
  }

  @Test
  def closedMultilineComment_line_break(): Unit = {
    """
    /*  one^two
     */
    class Foo {
    }
    """ becomes
    """
    /*  one
     *  ^two
     */
    class Foo {
    }
    """ after newline
  }

  @Test
  def closedDocComment_line_break_nested(): Unit = {
    """
    class Foo {
      /** one^two
       */
      def meth(): Unit = {}
    }
    """ becomes
    """
    class Foo {
      /** one
       *  ^two
       */
      def meth(): Unit = {}
    }
    """ after newline
  }

  @Test
  def closedDocComment_nop_end(): Unit = {
    """
    class Foo {
      /** one two *^/
      def meth(): Unit = {}
    }
    """ becomes
    """
    class Foo {
      /** one two *
       *  ^/
      def meth(): Unit = {}
    }
    """ after newline
  }

  @Test
  def closedDocComment_nop_beginning(): Unit = {
    """
    class Foo {
      /^** one two */
      def meth(): Unit = {}
    }
    """ becomes
    """
    class Foo {
      /
       * ^** one two */
      def meth(): Unit = {}
    }
    """ after newline
  }

  @Test
  def openDocComment_keep_indentation(): Unit = {
    """
    /**   hello^
    """ becomes
    """
    /**   hello
     *    ^
     */
    """ after newline
  }

  @Test
  def openMultilineComment_keep_indentation(): Unit = {
    """
    /*   hello^
    """ becomes
    """
    /*   hello
     *   ^
     */
    """ after newline
  }

  @Test
  def docComment_keep_indentation(): Unit = {
    """
    /**
     *    hello^
     */
    """ becomes
    """
    /**
     *    hello
     *    ^
     */
    """ after newline
  }

  @Test
  def multilineComment_keep_indentation(): Unit = {
    """
    /*
     *    hello^
     */
    """ becomes
    """
    /*
     *    hello
     *    ^
     */
    """ after newline
  }

  @Test
  def docComment_no_additional_indent_on_break_line_before_spaces(): Unit = {
    """
    /**^ abc */
    """ becomes
    """
    /**
     * ^ abc */
    """ after newline
  }

  @Test
  def docComment_wrap_text_after_cursor_on_automatically_closed_comment(): Unit = {
    """
    /** a^ b
    """ becomes
    """
    /** a
     *  ^ b
     */
    """ after newline
  }

  @Test
  def docComment_close_before_comment_with_code_blocks(): Unit = {
    """
    /**^
    /** {{{ }}} */
    """ becomes
    """
    /**
     * ^
     */
    /** {{{ }}} */
    """ after newline
  }

  @Test
  def docComment_no_close_before_code_blocks(): Unit = {
    """
    /**^ {{{ }}} */
    """ becomes
    """
    /**
     * ^ {{{ }}} */
    """ after newline
  }

  @Test
  def docComment_no_close_between_code_blocks(): Unit = {
    """
    /**
     * {{{ }}}
     * ^
     * {{{ }}}
     */
    """ becomes
    """
    /**
     * {{{ }}}
     * $
     * ^
     * {{{ }}}
     */
    """ after newline
  }

  @Test
  def docComment_close_before_string_containing_closing_comment(): Unit = {
    """
    /**^
    val str = " */"
    """ becomes
    """
    /**
     * ^
     */
    val str = " */"
    """ after newline
  }

  @Test
  def auto_break_on_print_margin() = """
    /*
     *      test tes^
     */
  """ becomes """
    /*
     *      test
     *      test^
     */
  """ after Add("t")

  @Test
  def no_auto_break_on_print_margin_when_feature_disabled() = disabled(P_ENABLE_AUTO_BREAKING_COMMENTS) { """
      /*
       *      test tes^
       */
    """ becomes """
      /*
       *      test test^
       */
    """ after Add("t")
  }

  @Test
  def auto_break_on_print_margin_when_no_star_exists() = """
    /*
      test test test^
     */
  """ becomes """
    /*
      test test
      tests^
     */
  """ after Add("s")

  @Test
  def no_auto_break_on_print_margin_with_only_one_ident() = """
    /*
     * testxtestxtes^
     */
  """ becomes """
    /*
     * testxtestxtest^
     */
  """ after Add("t")

  @Test
  def no_auto_break_on_print_margin_with_only_one_ident_when_no_star_exists() = """
    /*
xxxxxxtestxtestxtest^
     */
  """ becomes """
    /*
xxxxxxtestxtestxtests^
     */
  """ after Add("s")

  @Test
  def no_auto_break_on_print_margin_when_single_whitespace_is_inserted() = """
    /*
     * test test tes^
     */
  """ becomes """
    /*
     * test test tes ^
     */
  """ after Add(" ")

  @Test
  def auto_break_on_print_margin_when_text_after_single_whitespace_is_inserted() = """
    /*
     * test test tes ^
     */
  """ becomes """
    /*
     * test test tes
     *  ^
     */
  """ after Add(" ")

  @Test
  def auto_break_on_whitespace_and_not_on_ident_start() = """
    /*
     * test test `tes^
     */
  """ becomes """
    /*
     * test test
     * `tesx^
     */
  """ after Add("x")

  @Test
  def auto_break_on_first_line_of_comment() = """
    /* test test test^
     */
  """ becomes """
    /* test test
     * testx^
     */
  """ after Add("x")

 @Test
  def do_not_move_cursor_to_another_position_after_auto_break() = """
    /*
     * test testx^test
     */
  """ becomes """
    /*
     * test
     * testxx^test
     */
  """ after Add("x")

  @Test
  def no_auto_break_on_first_line_when_backspace_is_hit() = """
    /** testxtestxtestxtest
^
     */
  """ becomes """
    /** testxtestxtestxtest^
     */
  """ after Remove("\n")

}