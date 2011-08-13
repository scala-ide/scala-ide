package scala.tools.eclipse.hyperlinks

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.ScalaHyperlinkDetector
import scala.tools.nsc.interactive.Response
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jface.text.Region
import org.eclipse.ui.texteditor.ITextEditor
import org.junit.Assert._
import org.junit.Test
import scala.tools.eclipse.ScalaWordFinder
import scala.tools.eclipse.testsetup.TestProjectSetup
import org.eclipse.jface.text.IRegion

object HyperlinkDetectorTests extends TestProjectSetup("hyperlinks") {
  private final val HyperlinkMarker = "/*^*/"
  
  case class Link(pos: Pos, length: Int, text: String)
  case class Pos(line: Int, column: Int)
  
  /** Load a single Scala Compilation Unit. The file contains text markers that 
   * will be used to trigger hyperlinking requests to the presentation compiler. */
  def loadTestUnit(path2unit: String) = {
    val unit = scalaCompilationUnit(path2unit)
    reload(unit)
    new {
      /** @param expectations A collection of expected `Link` (test's oracle). */
      def andCheckAgainst(expectations: List[Link]) = {
        val positions = findMarker(HyperlinkMarker).in(unit)
  
        println("checking %d positions".format(positions.size))
        assertEquals(positions.size, expectations.size)
        for ((pos, oracle) <- positions.zip(expectations)) {
          val wordRegion = ScalaWordFinder.findWord(unit.getContents, pos)
          val word = new String(unit.getContents.slice(wordRegion.getOffset, wordRegion.getOffset + wordRegion.getLength))
          println("hyperlinking at position %d (%s)".format(pos, word))
          
          // Execute SUT
          val detector = new ScalaHyperlinkDetector
          val maybeLinks = detector.scalaHyperlinks(unit, wordRegion)
          
          // Verify Expectations
          assertTrue("no links found for `%s` @ (%d,%d)".format(word, oracle.pos.line, oracle.pos.column), maybeLinks.isDefined)
          val links = maybeLinks.get
          assertEquals("expected %d link, found %d".format(1, links.size), 1, links.size)
          val link = links.head
          assertEquals("text", oracle.text, link.getHyperlinkText())
          //assertEquals("offset", oracle.region.getOffset(), link.getHyperlinkRegion().getOffset())
          unit.withSourceFile({ (sourceFile, compiler) =>
            val offset = link.getHyperlinkRegion().getOffset()
            val length = link.getHyperlinkRegion().getLength
            val linkedPos = compiler.rangePos(sourceFile, offset, offset, offset + length)
            assertEquals("@line:",oracle.pos.line, linkedPos.line)
            assertEquals("@line:",oracle.pos.column, linkedPos.column)
          })(None)
          assertEquals("length", oracle.length, link.getHyperlinkRegion().getLength())
        }
      }
    }
  }
}

class HyperlinkDetectorTests {
  import HyperlinkDetectorTests._
  
  @Test
  def simpleHyperlinks() {
    val unit = scalaCompilationUnit("hyperlinks/SimpleHyperlinking.scala")

    reload(unit)
    
    val contents = unit.getContents
    val positions = SDTTestUtils.positionsOf(contents, "/*^*/")

    println("checking %d positions".format(positions.size))
    val detector = new ScalaHyperlinkDetector
    for (pos <- positions) {
      val wordRegion = ScalaWordFinder.findWord(unit.getContents, pos - 1)
      val word = new String(unit.getContents.slice(wordRegion.getOffset, wordRegion.getOffset + wordRegion.getLength))
      println("hyperlinking at position %d (%s)".format(pos, word))
      val links = detector.scalaHyperlinks(unit, wordRegion)
      println("Found links: " + links)
      assertTrue(links.isDefined)
      assertEquals(1, links.get.size)
    }
  }
  
  @Test
  def bug1000560() {
    val oracle = List(Link(Pos(12,10), 5, "object bug1000560.Outer"),
    			      Link(Pos(12,22), 3, "value bug1000560.Outer.bbb"),
    			      Link(Pos(12,37), 1, "value bug1000560.Outer.a"),
    			      Link(Pos(14,10), 5, "object bug1000560.Outer")
  )
    
    loadTestUnit("bug1000560/Test1.scala").andCheckAgainst(oracle)
  }
  
  @Test
  def bug1000560_2() {
    val oracle = List(Link(Pos(10,10), 3, "value bug1000560.Test2.foo"),
                      Link(Pos(10,20), 3, "method bug1000560.Foo.bar"))
    
    loadTestUnit("bug1000560/Test2.scala").andCheckAgainst(oracle)
  }
}