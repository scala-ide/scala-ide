package org.scalaide.core.ui

import org.eclipse.jdt.ui.text.IJavaPartitions
import org.junit.Before
import org.junit.Test
import org.scalaide.ui.internal.editor.autoedits.CommentAutoIndentStrategy
import org.scalaide.ui.internal.preferences.EditorPreferencePage._

import AutoEditStrategyTests._

class CommentAutoEditStrategyTest extends AutoEditStrategyTests(
    new CommentAutoIndentStrategy(
        prefStore, IJavaPartitions.JAVA_PARTITIONING)) {

  val newline = Add("\n")

  @Before
  def startUp() {
    enable(P_ENABLE_AUTO_CLOSING_COMMENTS, true)
  }

  @Test
  def no_close_on_deactiveted_feature() {
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
  def openDocComment_topLevel() {
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
  def openMultilineComment_topLevel() {
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
  def openDocComment_topLevel_with_nested() {
    """
    /**^
    class Foo {
      /** blah */
      def foo() {}
    }
    """ becomes
    """
    /**
     * ^
     */
    class Foo {
      /** blah */
      def foo() {}
    }
    """ after newline
  }

  @Test
  def openDocComment_topLevel_with_stringLit() {
    """
    /**^
    class Foo {
       def foo() {
            "/* */" // tricky, this trips the Java auto-edit :-D
      }
    }
    """ becomes
    """
    /**
     * ^
     */
    class Foo {
       def foo() {
            "/* */" // tricky, this trips the Java auto-edit :-D
      }
    }
    """ after newline
  }

  @Test
  def openDocComment_nested() {
    """
    /** blah */
    class Foo {
      /**^
      def foo() {
      }
    }
    """ becomes
    """
    /** blah */
    class Foo {
      /**
       * ^
       */
      def foo() {
      }
    }
    """ after newline
  }

  @Test
  def openDocComment_nested_with_other_docs() {
    """
    /** blah */
    class Foo {
      /**^
      def foo() {
      }
      /** */
      def bar
    }
    """ becomes
    """
    /** blah */
    class Foo {
      /**
       * ^
       */
      def foo() {
      }
      /** */
      def bar
    }
    """ after newline
  }

  @Test
  def closedDocComment_topLevel() {
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
  def closedMultilineComment_topLevel() {
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
  def closedDocComment_topLevel_nested() {
    """
    /** blah */
    class Foo {
      /**^*/
      def foo() {
      }
      /** */
      def bar
    }
    """ becomes
    """
    /** blah */
    class Foo {
      /**
       * ^*/
      def foo() {
      }
      /** */
      def bar
    }
    """ after newline
  }

  @Test
  def openDocComment_at_end() {
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
  def closedDocComment_no_asterisk_on_empty_line() {
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
  def closedMultilineComment_no_asterisk_on_empty_line() {
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
  def closedDocComment_no_asterisk_on_line_not_starting_with_asterisk() {
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
  def closedDocComment_line_break() {
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
  def closedMultilineComment_line_break() {
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
  def closedDocComment_line_break_nested() {
    """
    class Foo {
      /** one^two
       */
      def meth() {}
    }
    """ becomes
    """
    class Foo {
      /** one
       *  ^two
       */
      def meth() {}
    }
    """ after newline
  }

  @Test
  def closedDocComment_nop_end() {
    """
    class Foo {
      /** one two *^/
      def meth() {}
    }
    """ becomes
    """
    class Foo {
      /** one two *
       *  ^/
      def meth() {}
    }
    """ after newline
  }

  @Test
  def closedDocComment_nop_beginning() {
    """
    class Foo {
      /^** one two */
      def meth() {}
    }
    """ becomes
    """
    class Foo {
      /
       * ^** one two */
      def meth() {}
    }
    """ after newline
  }

  @Test
  def openDocComment_keep_indentation() {
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
  def openMultilineComment_keep_indentation() {
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
  def docComment_keep_indentation() {
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
  def multilineComment_keep_indentation() {
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
  def docComment_no_additional_indent_on_break_line_before_spaces() {
    """
    /**^ abc */
    """ becomes
    """
    /**
     * ^ abc */
    """ after newline
  }

  @Test
  def docComment_wrap_text_after_cursor_on_automatically_closed_comment() {
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
  def docComment_close_before_comment_with_code_blocks() {
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
  def docComment_no_close_before_code_blocks() {
    """
    /**^ {{{ }}} */
    """ becomes
    """
    /**
     * ^ {{{ }}} */
    """ after newline
  }

  @Test
  def docComment_no_close_between_code_blocks() {
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
  def docComment_close_before_string_containing_closing_comment() {
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

}