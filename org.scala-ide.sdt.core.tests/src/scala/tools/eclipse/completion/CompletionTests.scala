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
import scala.tools.eclipse.completion.ScalaCompletions

object CompletionTests extends TestProjectSetup("completion")


class CompletionTests {
  import CompletionTests._

  import org.eclipse.core.runtime.IProgressMonitor
  import org.eclipse.jface.text.IDocument
  import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext

  private def runTest(path2source: String)(expectedCompletions: List[String]*) {
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
      val content = unit.getContents.mkString /*.replace("/*!*/","")*/
      
      val completion = new ScalaCompletions
      for (i <- 0 until positions.size) yield {
        val pos = positions(i) 
        
        val position = new scala.tools.nsc.util.OffsetPosition(src, pos)
        var wordRegion = ScalaWordFinder.findWord(content, position.offset.get)
        
        
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
        
        val completions = completion.findCompletions(wordRegion)(pos+1, unit)(src, compiler)
        
        assertTrue("Found %d completions @ position %d (%d,%d), Expected %d".format(completions.size, pos, position.line, position.column, expectedCompletions(i).size),
            completions.size == expectedCompletions(i).size)
            
        completions.sortBy(c => c.display).zip(expectedCompletions(i).sorted).foreach {
          case (found, expected) => 
            assertTrue("Found `%s`, expected `%s`".format(found.display, expected), found.display == expected) 
        }
      }
    }()
  }
  
  @Test
  def ticket1000475() {
    val oraclePos73 = List("toString(): java.lang.String")
    val oraclePos116 = List(
        "formatLocal(java.util.Locale, Any*): String",
        "format(Any*): String",
        "foldRight[B](B)((Char, B) => B): B",
        "foldLeft[B](B)((B, Char) => B): B",
        "foldr[B](Int, Int, B, (Char, B) => B): B",
        "foldl[B](Int, Int, B, (B, Char) => B): B",
        "forall(Char => Boolean): Boolean",
        "foreach[U](Char => U): Unit",
        "fold[A1](A1)((A1, A1) => A1): A1",
        "formatted(String): String")
    val oraclePos147 = List(
        "formatLocal(java.util.Locale, Any*): String",
        "format(Any*): String",
        "foldRight[B](B)((Char, B) => B): B",
        "foldLeft[B](B)((B, Char) => B): B",
        "foldr[B](Int, Int, B, (Char, B) => B): B",
        "foldl[B](Int, Int, B, (B, Char) => B): B",
        "forall(Char => Boolean): Boolean",
        "foreach[U](Char => U): Unit",
        "fold[A1](A1)((A1, A1) => A1): A1",
        "formatted(String): String")
        
    runTest("ticket_1000475/Ticket1000475.scala")(oraclePos73, oraclePos116, oraclePos147)
  }
}