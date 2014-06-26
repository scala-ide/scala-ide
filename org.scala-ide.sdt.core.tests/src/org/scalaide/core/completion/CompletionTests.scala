package org.scalaide.core.completion

import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import scala.tools.nsc.interactive.Response
import org.scalaide.util.internal.ScalaWordFinder
import scala.reflect.internal.util.SourceFile
import org.scalaide.core.compiler.ScalaPresentationCompiler
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.junit.Assert._
import org.junit.Test
import org.scalaide.core.testsetup.TestProjectSetup
import org.eclipse.jdt.core.search.SearchEngine
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.core.search.IJavaSearchScope
import org.eclipse.jdt.core.search.SearchPattern
import org.eclipse.jdt.core.search.TypeNameRequestor
import org.eclipse.jdt.core.IJavaElement
import org.junit.Ignore
import scala.reflect.internal.util.OffsetPosition

object CompletionTests extends TestProjectSetup("completion")

@deprecated("Don't use this test class anymore. Use the test suite in org.scalaide.ui.completion instead", "4.0")
class CompletionTests {
  import CompletionTests._

  import org.eclipse.core.runtime.IProgressMonitor
  import org.eclipse.jface.text.IDocument
  import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext

  private def withCompletions(path2source: String)(body: (Int, OffsetPosition, List[CompletionProposal]) => Unit) {
    val unit = compilationUnit(path2source).asInstanceOf[ScalaCompilationUnit]

    // first, 'open' the file by telling the compiler to load it
    unit.withSourceFile { (src, compiler) =>
      val dummy = new Response[Unit]
      compiler.askReload(List(src), dummy)
      dummy.get

      val tree = new Response[compiler.Tree]
      compiler.askLoadedTyped(src, false, tree)
      tree.get

      val contents = unit.getContents
      // mind that the space in the marker is very important (the presentation compiler
      // seems to get lost when the position where completion is asked
      val positions = SDTTestUtils.positionsOf(contents, " /*!*/")
      assertTrue("Couldn't find a position for the completion marker. Hint: Did you add a space between the element to complete and the marker?", positions.nonEmpty)
      val content = unit.getContents.mkString

      val completion = new ScalaCompletions
      for (i <- 0 until positions.size) {
        val pos = positions(i)

        val position = new scala.reflect.internal.util.OffsetPosition(src, pos)
        val wordRegion = ScalaWordFinder.findWord(content, position.point)

        //        val selection = mock(classOf[ISelectionProvider])

        /* FIXME:
         * I would really love to call `completion.computeCompletionProposals`, but for some unclear
         * reason that call is not working. Some debugging shows that the position is not right (off by one),
         * however, increasing the position makes the computed `wordRegion` wrong... hard to understand where
         * the bug is!
        val textViewer = mock(classOf[ITextViewer])
        when(textViewer.getSelectionProvider()).thenReturn(selection)
        val document = mock(classOf[IDocument])
        when(document.get()).thenReturn(content)
        when(textViewer.getDocument()).thenReturn(document)
        val monitor = mock(classOf[IProgressMonitor])
        val context = new ContentAssistInvocationContext(textViewer, position.offset.get)
        import collection.JavaConversions._
        val completions: List[ICompletionProposal] = completion.computeCompletionProposals(context, monitor).map(_.asInstanceOf[ICompletionProposal]).toList
        */

        val completions = completion.findCompletions(wordRegion)(pos + 1, unit)(src, compiler)
        val sortedCompletions = completions.sortWith((x,y) => x.relevance >= y.relevance)
        body(i, position, sortedCompletions)
      }
    }
  }

  /**
   * @param withImportProposal take in account proposal for types not imported yet
   */
  private def runTest(path2source: String, withImportProposal: Boolean)(expectedCompletions: List[String]*) {

    withCompletions(path2source) { (i, position, compl) =>

      val completions = if (!withImportProposal) compl.filter(!_.needImport) else compl

      // remove parens as the compiler trees' printer has been slightly modified in 2.10
      // (and we need the test to pass for 2.9.0/-1 and 2.8.x as well).
      val completionsNoParens: List[String] = completions.map(c => normalizeCompletion(c.display)).sorted
      val expectedNoParens: List[String] = expectedCompletions(i).map(normalizeCompletion).sorted

      println("Found following completions @ position (%d,%d):".format(position.line, position.column))
      completionsNoParens.foreach(e => println("\t" + e))
      println()

      println("Expected completions:")
      expectedNoParens.foreach(e => println("\t" + e))
      println()

      assertTrue("Found %d completions @ position (%d,%d), Expected %d"
        .format(completionsNoParens.size, position.line, position.column, expectedNoParens.size),
        completionsNoParens.size == expectedNoParens.size) // <-- checked condition

      completionsNoParens.zip(expectedNoParens).foreach {
        case (found, expected) =>
          assertEquals("Wrong completion", expected, found)
      }
    }
  }

  /**
   * Transform the given completion proposal into a string that is (hopefully)
   *  compiler-version independent.
   *
   *  Transformations are:
   *    - remove parenthesis
   *    - java.lang.String => String
   */
  private def normalizeCompletion(str: String): String = {
    str.replace("(", "").replace(")", "").replace("java.lang.String", "String")
  }

  /**
   * This is more a structure builder problem, but it is visible through completion
   */

  def checkPackageNameOnSingleCompletion(sourcePath: String, expected: Seq[(String, String)]) {
    withCompletions(sourcePath) { (idx, position, completions) =>
      assertEquals("Only one completion expected at (%d, %d)".format(position.line, position.column), 1, completions.size)
      assertEquals("Unexpected package name", expected(idx)._1, completions(0).displayDetail)
      assertEquals("Unexpected the class name", expected(idx)._2, completions(0).display)
    }
  }

  @Test
  def ticket1000855_1() {
    checkPackageNameOnSingleCompletion("ticket_1000855/a/A.scala", Seq(("a.b", "T855B")))
  }

  @Test
  def ticket1000855_2() {
    checkPackageNameOnSingleCompletion("ticket_1000855/d/D.scala", Seq(("a.b.c", "T855C"), ("a.b.e", "T855E")))
  }

  @Test
  def relevanceSortingTests() {
    val unit = scalaCompilationUnit("relevance/RelevanceCompletions.scala")
    reload(unit)

    withCompletions("relevance/RelevanceCompletions.scala") { (idx, pos, proposals) =>
      idx match {
        case 0 =>
          assertEquals("Relevance", "__stringLikeClass", proposals.head.completion)
        case 1 =>
          assertEquals("Relevance", "List", proposals.head.completion)
        case _ =>
          assert(false, "Unhandled completion position")
      }
    }
  }

  @Test
  def t1001218() {
    val oraclePos8_14 = List("println(): Unit", "println(Any): Unit")
    val oraclePos10_12 = List("foo(): Int")
    val oraclePos12_12 = List("foo(): Int")
    val oraclePos18_10 = List("foo(): Int")

    val unit = scalaCompilationUnit("t1001218/A.scala")
    reload(unit)

    runTest("t1001218/A.scala", false)(oraclePos8_14, oraclePos10_12, oraclePos12_12, oraclePos18_10)
  }

  @Test
  def t1001272() {
    val oraclePos16_18 = List("A(): t1001272.A", "A(Int): t1001272.A")
    val oraclePos17_18 = List("B(): t1001272.B")
    val oraclePos18_20 = List("E(Int): t1001272.D.E")
    val oraclePos19_26 = List("InnerA(Int): t1001272.Test.a.InnerA")

    val unit = scalaCompilationUnit("t1001272/A.scala")
    reload(unit)

    runTest("t1001272/A.scala", false)(oraclePos16_18, oraclePos17_18, oraclePos18_20, oraclePos19_26)
  }

  @Ignore("Enable this when ticket #1001919 is fixed.")
  @Test
  def t1001919() {
    withCompletions("t1001919/Ticket1001919.scala") {
      (index, position, completions) =>
        assertEquals("There is only one completion location", 1, completions.size)
        assertTrue("The completion should return doNothingWith", completions.exists(
          _ match {
            case c:CompletionProposal =>
              c.kind == MemberKind.Def && c.context == CompletionContext(CompletionContext.ImportContext) && c.completion == "doNothingWith"
            case _ =>
              false
          }))
    }
  }

  @Test
  def t1002002() {
    withCompletions("t1002002/A.scala") {
      (index, position, completions) =>
        assertEquals("There is only one completion location", 1, completions.size)
        assertTrue("The completion should return completion", completions exists {
            case c: CompletionProposal =>
              assertTrue("Should need import", c.needImport)
              assertEquals("test.A.ATestInner", c.fullyQualifiedName)
              true
            case a => println(s"Got: $a")
              false
        })
    }
  }

  @Test
  def t1002002_2() {
    withCompletions("t1002002/D.scala") {
      (index, position, completions) =>
        assertEquals("There is only one completion location", 1, completions.size)
        assertTrue("The completion should return completion", completions exists {
            case c: CompletionProposal =>
              assertTrue("Should need import", c.needImport)
              assertEquals("test.A.C.ACTestInner", c.fullyQualifiedName)
              true
            case a => println(s"Got: $a")
              false
        })
    }
  }

  @Test
  def backticks_completion_t1001371() {
    withCompletions("backticks_completion/BackticksCompletionDemo.scala") {
      (testNumber, _, proposals) => {
        def `assert completes with    backticks`(what: String, completion: String) {
          assertTrue(s"$what should auto-complete WITH backticks.", proposals.exists(_.completion == s"`$completion`"))
        }

        def `assert completes without backticks`(what: String, completion: String) {
          assertTrue(s"$what should auto-complete WITHOUT backticks.", proposals.exists(_.completion == completion))
        }

        testNumber match {
          case 0 => `assert completes without backticks`("A normal class name", "NormalClassName")
          case 1 => `assert completes with    backticks`("A non-standard class name", "weird class name")
          case 2 => `assert completes without backticks`("A weird but valid class name", "abcαβγ_!^©®")
          case 3 => `assert completes with    backticks`("A non-standard trait name", "misnamed/trait")
          case 4 => `assert completes with    backticks`("A non-standard object name", "YOLO Obj")
          case 5 => `assert completes without backticks`("A def with a standard name", "normalDef")
          case 6 => `assert completes with    backticks`("A def with a non-standard name", "weird/def")
          case 7 => `assert completes with    backticks`("A field with a non-standard name", "text/plain")
          case 8 => `assert completes without backticks`("A field with a weird but valid name", "lolcαβγ_!^©®")
          case 9 => `assert completes with    backticks`("A field with an illegal char in its name", "badchar£2")
          case 10=> `assert completes with    backticks`("An identifier that is a reserved word", "while")
        }
      }
    }
  }
}
