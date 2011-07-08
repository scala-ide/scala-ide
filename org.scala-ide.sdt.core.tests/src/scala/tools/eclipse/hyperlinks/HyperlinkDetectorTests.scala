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

object HyperlinkDetectorTests extends TestProjectSetup("hyperlinks")

class HyperlinkDetectorTests {
  import HyperlinkDetectorTests._
  
  @Test
  def simpleHyperlinks() {
    val unit = compilationUnit("hyperlinks/SimpleHyperlinking.scala").asInstanceOf[ScalaCompilationUnit];

    // first, 'open' the file by telling the compiler to load it
    project.withSourceFile(unit) { (src, compiler) =>
      val dummy = new Response[Unit]
      compiler.askReload(List(src), dummy)
      dummy.get

      val tree = new Response[compiler.Tree]
      compiler.askType(src, false, tree)
      tree.get
    }()

    val contents = unit.getContents
    val positions = SDTTestUtils.positionsOf(contents, "/*^*/")

    println("checking %d positions".format(positions.size))
    val detector = new ScalaHyperlinkDetector
    for (pos <- positions) {
      println("hyperlinking at position %d".format(pos))
      val wordRegion = ScalaWordFinder.findWord(unit.getContents, pos - 1)
      val links = detector.scalaHyperlinks(unit, wordRegion)
      println("Found links: " + links)
      assertTrue(links.isDefined)
      assertEquals(1, links.get.size)
    }
  }
}