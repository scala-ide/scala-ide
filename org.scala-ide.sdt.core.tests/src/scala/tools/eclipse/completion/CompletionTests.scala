package scala.tools.eclipse.completion

import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.nsc.interactive.Response
import scala.tools.eclipse.ScalaWordFinder
import scala.reflect.internal.util.SourceFile
import scala.tools.eclipse.ScalaPresentationCompiler
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.junit.Assert._
import org.junit.Test
import scala.tools.eclipse.testsetup.TestProjectSetup
import org.eclipse.jdt.core.search.SearchEngine
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.core.search.IJavaSearchScope
import org.eclipse.jdt.core.search.SearchPattern
import org.eclipse.jdt.core.search.TypeNameRequestor
import org.eclipse.jdt.core.IJavaElement
import org.junit.Ignore
import scala.reflect.internal.util.OffsetPosition

object CompletionTests extends TestProjectSetup("completion")

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
      compiler.askType(src, true, tree)
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
   * Test that completion shows only accessible members.
   */
  @Test
  def accessibilityTests() {
    val oraclePos14 = List("secretPrivate: Unit",
      "secretProtected: Unit",
      "secretProtectedInPackage: Unit",
      "secretPublic: Unit")

    val oraclePos16 = List("secretPrivate: Unit",
      "secretPrivateThis: Unit",
      "secretProtected: Unit",
      "secretProtectedInPackage: Unit",
      "secretPublic: Unit")
    val oraclePos22 = List(
      "secretProtected: Unit",
      "secretProtectedInPackage: Unit",
      "secretPublic: Unit")
    val oraclePos28 = List(
      "secretProtectedInPackage: Unit",
      "secretPublic: Unit")
    val oraclePos37 = List(
      "secretPublic: Unit")

    runTest("accessibility/AccessibilityTest.scala", true)(oraclePos14, oraclePos16, oraclePos22, oraclePos28, oraclePos37)
  }

  @Test
  def ticket1000475() {
    val oraclePos73 = List("toString(): String")
    val oraclePos116 = List("forallChar => Boolean: Boolean")
    val oraclePos147 = List("forallChar => Boolean: Boolean")

    runTest("ticket_1000475/Ticket1000475.scala", false)(oraclePos73, oraclePos116, oraclePos147)
  }

  /**
   * Test completion for 'any' Java type visible in the project
   */
  @Test
  def ticket1000476() {
    val oraclePos4_26 = List("ArrayList", "ArrayList", "ArrayLister") // ArrayList also from java.util.Arrays
    val oraclePos6_33 = List("ArrayList")
    val oraclePos11_16 = List("TreeSet")

    runTest("ticket_1000476/Ticket1000476.scala", true)(oraclePos4_26, oraclePos6_33, oraclePos11_16)
  }

  @Test
  def ticket1000654() {
    val oraclePos10_13 = List("t654_a(String): Unit", "t654_a(Int): Unit")

    runTest("ticket_1000654/Ticket1000654.scala", true)(oraclePos10_13)
  }

  @Test
  def ticket1000772() {
    val OracleNames = List(List("param1", "param2"), List("secondSectionParam1"))
    withCompletions("ticket_1000772/CompletionsWithName.scala") { (idx, position, completions) =>
      assertEquals("Only one completion expected at (%d, %d)".format(position.line, position.column), 1, completions.size)
      assertEquals("Expected the following names: %s".format(OracleNames),
        OracleNames, completions(0).getParamNames())
    }
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
  @Ignore("Enable this test once the ticket is fixed.")
  def t1001014() {
    val oracle = List("xx")

    val unit = scalaCompilationUnit("t1001014/F.scala")
    reload(unit)

    runTest("t1001014/F.scala", false)(oracle)
  }

  @Test
  def t1001207() {
    val unit = scalaCompilationUnit("ticket_1001207/T1207.scala")
    reload(unit)

    withCompletions("ticket_1001207/T1207.scala") {
      (index, position, completions) =>
        assertEquals("There is only one completion location", 0, index)
        assertTrue("The completion should return java.util", completions.exists(
          _ match {
            case CompletionProposal(MemberKind.Package, _, _, "util", _, _, _, _, _, _, _, _) =>
              true
            case _ =>
              false
          }))
    }
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
  def empty_parens_completion_ticket1001766() {
    withCompletions("ticket_1001766/Ticket1001766.scala") {
      (index, position, completions) =>
        index match {
        case 0 =>
          assertTrue("The completion should return the `buz` method name with empty-parens", completions.exists {
            case CompletionProposal(MemberKind.Def, _, _, "buz", _, _, _, _, getParamNames, _, _, _) =>
              getParamNames() == List(Nil) // List(Nil) is how an empty-args list is encoded
        })
        case 1 =>
          assertTrue("The completion should return the `bar` method name with NO empty-parens", completions.exists {
            case CompletionProposal(MemberKind.Def, _, _, "bar", _, _, _, _, getParamNames, _, _, _) =>
              getParamNames() == Nil
          })
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

  @Test
  def t1001125() {
    withCompletions("t1001125/Ticket1001125.scala") {
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

}
