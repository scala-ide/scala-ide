package scala.tools.eclipse.hyperlinks

import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.tools.eclipse.ScalaHyperlinkDetector
import scala.tools.eclipse.ScalaWordFinder

import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

object HyperlinkDetectorTests extends TestProjectSetup("hyperlinks") with HyperlinkTester

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
    val oracle = List(Link("object bug1000560.Outer"),
    			      Link("value bug1000560.Outer.bbb"),
    			      Link("value bug1000560.Outer.a"),
    			      Link("object bug1000560.Outer")
  )
    
    loadTestUnit("bug1000560/Test1.scala").andCheckAgainst(oracle)
  }
  
  @Test @Ignore
  def bug1000560_2() {
    val oracle = List(Link("value bug1000560.Test2.foo"),
                      Link("method bug1000560.Foo.bar"))
    
    loadTestUnit("bug1000560/Test2.scala").andCheckAgainst(oracle)
  }
  
  @Test
  def test1000656() {
    SDTTestUtils.enableAutoBuild(false)  // make sure no auto-building is happening
    
    object hyperlinksSubProject extends TestProjectSetup("hyperlinks-sub")
    hyperlinksSubProject.project        // force initialization of this project
    hyperlinksSubProject.project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
    
    val markers = SDTTestUtils.findProblemMarkers(hyperlinksSubProject.compilationUnit("util/Box.scala")).toList
    val errorMessages: List[String] = for (p <- markers) yield p.getAttribute(IMarker.MESSAGE).toString
    
    println(errorMessages)
    
    assertTrue("No build errors expected", errorMessages.isEmpty)

    // since auto-building is off, we need to do this manually
    // and make sure the classpath is up to date
    project.resetPresentationCompiler()
    
    val oracle = List(Link("type util.Box.myInt"), Link("method util.Full.apply"))
    loadTestUnit("bug1000656/Client.scala").andCheckAgainst(oracle)
  }
}
