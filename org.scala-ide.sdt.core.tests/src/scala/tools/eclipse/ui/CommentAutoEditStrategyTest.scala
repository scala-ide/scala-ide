package scala.tools.eclipse.ui

import scala.tools.eclipse.lexical.ScalaDocumentPartitioner

import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.text.{Document, IDocument}
import org.junit.{ComparisonFailure, Test}

import AutoEditStrategyTests.TestCommand

class CommentAutoEditStrategyTest {

  /**
   * Tests if the input string is equal to the expected output.
   *
   * For each input and output string, there must be set the cursor position
   * which is denoted by a ^ sign and must occur once.
   *
   * Sometimes it can happen that the input or output must contain trailing
   * white spaces. If this is the case then a $ sign must be set to the position
   * after the expected number of white spaces.
   */
  def test(input: String, expectedOutput: String) {
    require(input.count(_ == '^') == 1, "the cursor in the input isn't set correctly")
    require(expectedOutput.count(_ == '^') == 1, "the cursor in the expected output isn't set correctly")

    def createDocument(input: String): IDocument = {
      val rawInput = input.filterNot(_ == '^')
      val doc = new Document(rawInput)
      val partitioner = new ScalaDocumentPartitioner

      doc.setDocumentPartitioner(IJavaPartitions.JAVA_PARTITIONING, partitioner)
      partitioner.connect(doc)
      doc
    }

    def createTestCommand(input: String): TestCommand = {
      val pos = input.indexOf('^')
      new TestCommand(pos, 0, "\n", -1, false, true)
    }

    val doc = createDocument(input)
    val cmd = createTestCommand(input)
    val strategy = new CommentAutoIndentStrategy(IJavaPartitions.JAVA_PARTITIONING)

    strategy.customizeDocumentCommand(doc, cmd)
    doc.replace(cmd.offset, 0, cmd.text)

    val offset = if (cmd.caretOffset > 0) cmd.caretOffset else cmd.offset + cmd.text.length()
    doc.replace(offset, 0, "^")

    val expected = expectedOutput.replaceAll("\\$", "")
    val actual = doc.get()

    if (expected != actual) {
      throw new ComparisonFailure("", expected, actual)
    }
  }

  @Test
  def openDocComment_topLevel() {
    val input =
      """
      /**^
      class Foo
      """
    val expectedOutput =
      """
      /**
       * ^
       */
      class Foo
      """
    test(input, expectedOutput)
  }

  @Test
  def openMultilineComment_topLevel() {
    val input =
      """
      /*^
      class Foo
      """
    val expectedOutput =
      """
      /*
       * ^
       */
      class Foo
      """
    test(input, expectedOutput)
  }

  @Test
  def openDocComment_topLevel_with_nested() {
    val input =
      """
      /**^
      class Foo {
        /** blah */
        def foo() {}
      }
      """
    val expectedOutput =
      """
      /**
       * ^
       */
      class Foo {
        /** blah */
        def foo() {}
      }
      """
    test(input, expectedOutput)
  }

  @Test
  def openDocComment_topLevel_with_stringLit() {
    val input =
      """
      /**^
      class Foo {
         def foo() {
              "/* */" // tricky, this trips the Java auto-edit :-D
        }
      }
      """
    val expectedOutput =
      """
      /**
       * ^
       */
      class Foo {
         def foo() {
              "/* */" // tricky, this trips the Java auto-edit :-D
        }
      }
      """
    test(input, expectedOutput)
  }

  @Test
  def openDocComment_nested() {
    val input =
      """
      /** blah */
      class Foo {
        /**^
        def foo() {
        }
      }
      """
    val expectedOutput =
      """
      /** blah */
      class Foo {
        /**
         * ^
         */
        def foo() {
        }
      }
      """
    test(input, expectedOutput)
  }

  @Test
  def openDocComment_nested_with_other_docs() {
    val input =
      """
      /** blah */
      class Foo {
        /**^
        def foo() {
        }
        /** */
        def bar
      }
      """
    val expectedOutput =
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
      """
    test(input, expectedOutput)
  }

  @Test
  def closedDocComment_topLevel() {
    val input =
      """
      /** ^blah */
      class Foo {
      }
      """
    val expectedOutput =
      """
      /** $
       *  ^blah */
      class Foo {
      }
      """
    test(input, expectedOutput)
  }

  @Test
  def closedMultilineComment_topLevel() {
    val input =
      """
      /*  ^blah */
      class Foo {
      }
      """
    val expectedOutput =
      """
      /*  $
       *  ^blah */
      class Foo {
      }
      """
    test(input, expectedOutput)
  }

  @Test
  def closedDocComment_topLevel_nested() {
    val input =
      """
      /** blah */
      class Foo {
        /**^*/
        def foo() {
        }
        /** */
        def bar
      }
      """
    val expectedOutput =
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
      """
    test(input, expectedOutput)
  }

  @Test
  def openDocComment_at_beginning() {
    val input =
      """
      /**^class Foo {
        def foo() {
        }
        /** */
        def bar
      }
      """
    val expectedOutput =
      """
      /**
       * ^
       */class Foo {
        def foo() {
        }
        /** */
        def bar
      }
      """
    test(input, expectedOutput)
  }

  @Test
  def openDocComment_at_end() {
    val input =
      """
      class Foo {
      }/**^
      """
    val expectedOutput =
      """
      class Foo {
      }/**
      * ^
      */
      """
    test(input, expectedOutput)
  }

  @Test
  def closedDocComment_no_asterisk_on_empty_line() {
    val input =
      """
      /**
      ^
      */
      class Foo {
      }
      """
    val expectedOutput =
      """
      /**
      $
      ^
      */
      class Foo {
      }
      """
    test(input, expectedOutput)
  }

  @Test
  def closedMultilineComment_no_asterisk_on_empty_line() {
    val input =
      """
      /*
      ^
      */
      class Foo {
      }
      """
    val expectedOutput =
      """
      /*
      $
      ^
      */
      class Foo {
      }
      """
    test(input, expectedOutput)
  }

  @Test
  def closedDocComment_no_asterisk_on_line_not_starting_with_asterisk() {
    val input =
      """
      /**
      hello^
      */
      class Foo {
      }
      """
    val expectedOutput =
      """
      /**
      hello
      ^
      */
      class Foo {
      }
      """
    test(input, expectedOutput)
  }

  @Test
  def closedDocComment_line_break() {
    val input =
      """
      /** one^two
       */
      class Foo {
      }
      """
    val expectedOutput =
      """
      /** one
       *  ^two
       */
      class Foo {
      }
      """
    test(input, expectedOutput)
  }

  @Test
  def closedMultilineComment_line_break() {
    val input =
      """
      /*  one^two
       */
      class Foo {
      }
      """
    val expectedOutput =
      """
      /*  one
       *  ^two
       */
      class Foo {
      }
      """
    test(input, expectedOutput)
  }

  @Test
  def closedDocComment_line_break_nested() {
    val input =
      """
      class Foo {
        /** one^two
         */
        def meth() {}
      }
      """
    val expectedOutput =
      """
      class Foo {
        /** one
         *  ^two
         */
        def meth() {}
      }
      """
    test(input, expectedOutput)
  }

  @Test
  def closedDocComment_nop_end() {
    val input =
      """
      class Foo {
        /** one two *^/
        def meth() {}
      }
      """
    val expectedOutput =
      """
      class Foo {
        /** one two *
         *  ^/
        def meth() {}
      }
      """
    test(input, expectedOutput)
  }

  @Test
  def closedDocComment_nop_beginning() {
    val input =
      """
      class Foo {
        /^** one two */
        def meth() {}
      }
      """
    val expectedOutput =
      """
      class Foo {
        /
         * ^** one two */
        def meth() {}
      }
      """
    test(input, expectedOutput)
  }

  @Test
  def openDocComment_keep_indentation() {
    val input =
      """
      /**   hello^
      """
    val expectedOutput =
      """
      /**   hello
       *    ^
       */
      """
    test(input, expectedOutput)
  }

  @Test
  def openMultilineComment_keep_indentation() {
    val input =
      """
      /*   hello^
      """
    val expectedOutput =
      """
      /*   hello
       *   ^
       */
      """
    test(input, expectedOutput)
  }

  @Test
  def docComment_keep_indentation() {
    val input =
      """
      /**
       *    hello^
       */
      """
    val expectedOutput =
      """
      /**
       *    hello
       *    ^
       */
      """
    test(input, expectedOutput)
  }

  @Test
  def multilineComment_keep_indentation() {
    val input =
      """
      /*
       *    hello^
       */
      """
    val expectedOutput =
      """
      /*
       *    hello
       *    ^
       */
      """
    test(input, expectedOutput)
  }

}