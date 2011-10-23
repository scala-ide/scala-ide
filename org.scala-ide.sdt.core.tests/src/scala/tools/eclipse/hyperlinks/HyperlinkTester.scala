package scala.tools.eclipse.hyperlinks

import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.tools.eclipse.ScalaHyperlinkDetector
import scala.tools.eclipse.ScalaWordFinder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue


trait HyperlinkTester extends TestProjectSetup {
  protected type VerifyHyperlink = {
    def andCheckAgainst(expectations: List[Link]): Unit
  }
  
  private final val HyperlinkMarker = "/*^*/"

  case class Link(text: String)

  /** Given a source `path`, load the corresponding scala `unit`. */
  def loadTestUnit(path: String): VerifyHyperlink = {
    val unit = scalaCompilationUnit(path)
    loadTestUnit(unit)
  }
  
  /** Load a scala `unit` that contains text markers used 
   *  to generate hyperlinking requests to the presentation compiler.
   */
  def loadTestUnit(unit: ScalaUnit): VerifyHyperlink = {
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
          assertTrue("no links found for `%s`".format(word), maybeLinks.isDefined)
          val links = maybeLinks.get
          assertEquals("expected %d link, found %d".format(1, links.size), 1, links.size)
          val link = links.head
          assertEquals("text", oracle.text, link.getHyperlinkText())
          //assertEquals("offset", oracle.region.getOffset(), link.getHyperlinkRegion().getOffset())
          unit.withSourceFile({ (sourceFile, compiler) =>
            val offset = link.getHyperlinkRegion().getOffset()
            val length = link.getHyperlinkRegion().getLength
            val linkedPos = compiler.rangePos(sourceFile, offset, offset, offset + length)
          })(None)
        }
      }
    }
  }
}