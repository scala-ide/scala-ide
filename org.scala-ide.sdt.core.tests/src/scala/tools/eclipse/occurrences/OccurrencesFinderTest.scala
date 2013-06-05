package scala.tools.eclipse.occurrences

import scala.tools.eclipse.javaelements._
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.core.JavaCore
import scala.tools.eclipse.testsetup.SDTTestUtils
import org.junit.Assert._
import org.eclipse.core.runtime.Path
import org.junit.Test
import org.junit.Ignore
import org.eclipse.jface.text.Region
import scala.tools.eclipse.markoccurrences.ScalaOccurrencesFinder
import scala.tools.eclipse.ScalaWordFinder
import scala.tools.nsc.interactive.Response
import scala.tools.eclipse.testsetup.TestProjectSetup

object OccurrencesFinderTest extends TestProjectSetup("occurrences-hyperlinking")

class OccurrencesFinderTest {
  import OccurrencesFinderTest._

  @Test def typeOccurrences() {
    val unit = compilationUnit("occ/DummyOccurrences.scala").asInstanceOf[ScalaCompilationUnit];

    // first, 'open' the file by telling the compiler to load it
    project.withSourceFile(unit) { (src, compiler) =>
      val dummy = new Response[Unit]
      compiler.askReload(List(src), dummy)
      dummy.get

      val tree =  new Response[compiler.Tree]
      compiler.askType(src, false,tree)
      tree.get
    }()

    val contents = unit.getContents
    val positions = SDTTestUtils.markersOf(contents, "<")

    println("checking %d positions".format(positions.size))

    for ((pos, count) <- positions) {
      println("looking at position %d for %d occurrences".format(pos, count))
      val region = ScalaWordFinder.findWord(contents, pos - 1)
      val word = new String(contents.slice(region.getOffset(), region.getOffset() + region.getLength()))
      println("using word region: " + region)
      val occurrences = new ScalaOccurrencesFinder(unit).findOccurrences(region, 1)
      assertTrue("No occurrences of %s".format(word), occurrences.isDefined)
      assertEquals("Not enough occurrences (%s): expected: %d, found: %d".format(word, count, occurrences.get.locations.size), count, occurrences.get.locations.size)
    }
  }
}