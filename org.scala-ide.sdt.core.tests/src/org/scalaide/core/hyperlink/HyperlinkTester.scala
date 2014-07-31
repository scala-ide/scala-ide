package org.scalaide.core
package hyperlink

import testsetup.TestProjectSetup
import org.scalaide.util.internal.ScalaWordFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.scalaide.core.hyperlink.detector.ScalaDeclarationHyperlinkComputer
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.eclipse.jface.text.IRegion
import org.scalaide.core.hyperlink.detector.DeclarationHyperlinkDetector
import org.scalaide.core.hyperlink.detector.JavaSelectionEngine
import org.eclipse.jdt.internal.core.Openable
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IType

import scala.language.reflectiveCalls

trait HyperlinkTester extends TestProjectSetup {
  trait VerifyHyperlink {
    def andCheckAgainst(expectations: List[Link], checker: (InteractiveCompilationUnit, IRegion, String, Link) => Unit = checkScalaLinks): Unit
  }

  private final val HyperlinkMarker = "/*^*/"

  case class Link(text: String*)

  /** Given a source `path`, load the corresponding scala `unit`. */
  def loadTestUnit(path: String, forceTypeChecking: Boolean = false): VerifyHyperlink = {
    val unit = scalaCompilationUnit(path)
    loadTestUnit(unit, forceTypeChecking)
  }

  /** Load a scala `unit` that contains text markers used
   *  to generate hyperlinking requests to the presentation compiler.
   */
  def loadTestUnit(unit: ScalaSourceFile, forceTypeChecking: Boolean): VerifyHyperlink = {
    reload(unit)
    if(forceTypeChecking) waitUntilTypechecked(unit)
    new VerifyHyperlink {
      /** @param expectations A collection of expected `Link` (test's oracle). */
      def andCheckAgainst(expectations: List[Link], checker: (InteractiveCompilationUnit, IRegion, String, Link) => Unit = checkScalaLinks) = {
        val positions = findMarker(HyperlinkMarker).in(unit)

        println("checking %d positions".format(positions.size))
        assertEquals(positions.size, expectations.size)
        for ((pos, oracle) <- positions.zip(expectations)) {
          val wordRegion = ScalaWordFinder.findWord(unit.getContents, pos)
          val word = new String(unit.getContents.slice(wordRegion.getOffset, wordRegion.getOffset + wordRegion.getLength))
          println("hyperlinking at position %d (%s)".format(pos, word))
          checker(unit, wordRegion, word, oracle)
        }
      }
    }
  }

  def checkScalaLinks(unit: InteractiveCompilationUnit, wordRegion: IRegion, word: String, oracle: Link) {
    val resolver = new ScalaDeclarationHyperlinkComputer
    val maybeLinks = resolver.findHyperlinks(unit, wordRegion)

    // Verify Expectations
    assertTrue("no links found for `%s`".format(word), maybeLinks.isDefined)
    val links = maybeLinks.get
    assertEquals("expected %d link, found %d".format(oracle.text.size, links.size), oracle.text.size, links.size)
    val linkResults = links map (_.getTypeLabel)
    assertEquals("text", oracle.text.toList.toString, linkResults.toString)
  }

  def checkJavaElements(unit: InteractiveCompilationUnit, wordRegion: IRegion, word: String, oracle: Link) {
    val elements = JavaSelectionEngine.getJavaElements(unit, unit.asInstanceOf[Openable], wordRegion)

    // Verify Expectations
    assertTrue("no links found for `%s`".format(word), elements.nonEmpty)
    assertEquals("expected %d link, found %d".format(oracle.text.size, elements.size), oracle.text.size, elements.size)
    val linkResults = for {
      e <- elements
      tpe = e.getAncestor(IJavaElement.TYPE).asInstanceOf[IType]
    } yield tpe.getFullyQualifiedName() + "." + e.getElementName()
    assertEquals("text", oracle.text.toString, linkResults.toString)
  }
}