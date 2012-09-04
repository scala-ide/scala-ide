package scala.tools.eclipse.completion

import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.nsc.interactive.Response
import scala.tools.eclipse.ScalaWordFinder
import scala.tools.nsc.util.SourceFile
import scala.tools.eclipse.ScalaPresentationCompiler
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.mockito.Mockito._
import org.junit.Assert._
import org.junit.Test
import scala.tools.eclipse.testsetup.TestProjectSetup
import org.eclipse.jdt.core.search.{ SearchEngine, IJavaSearchConstants, IJavaSearchScope, SearchPattern, TypeNameRequestor }
import org.eclipse.jdt.core.IJavaElement
import org.junit.Ignore
import scala.tools.nsc.util.OffsetPosition

object CompletionTests extends TestProjectSetup("completion")

class CompletionTests {
  import CompletionTests._

  import org.eclipse.core.runtime.IProgressMonitor
  import org.eclipse.jface.text.IDocument
  import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext

  private def withCompletions(path2source: String)(body: (Int, OffsetPosition, List[CompletionProposal]) => Unit) {
    val unit = compilationUnit(path2source).asInstanceOf[ScalaCompilationUnit]

    // first, 'open' the file by telling the compiler to load it
    project.withSourceFile(unit) { (src, compiler) =>
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
      val content = unit.getContents.mkString

      val completion = new ScalaCompletions
      for (i <- 0 until positions.size) {
        val pos = positions(i)

        val position = new scala.tools.nsc.util.OffsetPosition(src, pos)
        var wordRegion = ScalaWordFinder.findWord(content, position.point)

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

        body(i, position, completion.findCompletions(wordRegion)(pos + 1, unit)(src, compiler))
      }
    }()
  }

  /**
   * @param withImportProposal take in account proposal for types not imported yet
   */
  private def runTest(path2source: String, withImportProposal: Boolean)(expectedCompletions: List[String]*) {

    withCompletions(path2source) { (i, position, compl) =>

      var completions = if (!withImportProposal) compl.filter(!_.needImport) else compl

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
          assertTrue("Found `%s`, expected `%s`".format(found, expected), found == expected)
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
    val oraclePos4_26 = List("ArrayList", "ArrayLister")
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
        OracleNames, completions(0).explicitParamNames)
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
  def t1001014 {
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
            case CompletionProposal(MemberKind.Package, _, "util", _, _, _, _, _, _, _, _, _) =>
              true
            case _ =>
              false
          }))
    }
  }
}