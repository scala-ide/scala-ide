package scala.tools.eclipse.hyperlink

import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.tools.eclipse.ScalaWordFinder
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test
import scala.tools.eclipse.hyperlink.text.detector.ScalaDeclarationHyperlinkComputer

object HyperlinkDetectorTests extends TestProjectSetup("hyperlinks") with HyperlinkTester

class HyperlinkDetectorTests {
  import HyperlinkDetectorTests._

  @Test
  def simpleHyperlinks() {
    val oracle = List(
      Link("type scala.Predef.Set"),
      Link("type hyperlinks.SimpleHyperlinking.Tpe"),
      Link("method scala.Array.apply", "object scala.Array"),
      Link("method scala.collection.TraversableOnce.sum"),
      Link("type scala.Predef.String"),
      Link("object scala.Some"),
      Link("class scala.Option"),
      Link("type scala.Predef.String"))

    loadTestUnit("hyperlinks/SimpleHyperlinking.scala").andCheckAgainst(oracle)
  }

  @Test
  def scalaPackageLinks() {
    val oracle = List(
        Link("method scala.collection.immutable.List.apply", "object scala.collection.immutable.List"),
        Link("object scala.collection.immutable.List"),
        Link("method scala.collection.generic.GenericCompanion.apply", "object scala.collection.Seq"),
        Link("object scala.collection.Seq"),
        Link("object scala.collection.immutable.Nil"),
        Link("method scala.collection.LinearSeqOptimized.apply", "value scalalinks.Foo.xs")
    )

    loadTestUnit("scalalinks/ScalaListLinks.scala").andCheckAgainst(oracle)
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

    val oracle = List(Link("type util.Box.myInt"), Link("method util.Full.apply", "object util.Full"))
    loadTestUnit("bug1000656/Client.scala").andCheckAgainst(oracle)
  }

  @Test
  def hyperlinkOnList_t1001215() {
    val oracle = List(Link("method scala.collection.immutable.List.apply", "object scala.collection.immutable.List"))

    loadTestUnit("t1001215/A.scala").andCheckAgainst(oracle)
  }
}
