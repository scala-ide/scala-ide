package scala.tools.eclipse.semantic

import org.junit.Test
import org.junit.Assert
import scala.collection.JavaConversions.mapAsScalaMap
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.semantichighlighting.implicits.ImplicitHighlightingPresenter
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.nsc.interactive.Response
import scala.tools.nsc.util.SourceFile
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.properties.ImplicitsPreferencePage
import org.junit.Before
import scala.tools.eclipse.javaelements.ScalaCompilationUnit

object ImplicitsHighlightingTest extends TestProjectSetup("implicits-highlighting")

class ImplicitsHighlightingTest {

  @Before
  def setPreferences() {
    ScalaPlugin.plugin.getPreferenceStore.setValue(ImplicitsPreferencePage.P_CONVERSIONS_ONLY, false)
  }

  @Test
  def implicitConversion() {
    withCompilationUnitAndCompiler("implicit-highlighting/Implicits.scala") { (src, compiler) =>

      val expected = List(
        "Implicit conversions found: List(1,2) => listToString(List(1,2)) [180, 9]",
        "Implicit conversions found: List(1,2,3) => listToString(List(1,2,3)) [151, 11]"
      )
      val actual = implicits(src, compiler)

      assertSameLists(expected, actual)
    }
  }

  @Test
  def implicitConversionsFromPredef() {
    withCompilationUnitAndCompiler("implicit-highlighting/DefaultImplicits.scala") { (src, compiler) =>

      val expected = List(
        "Implicit conversions found: 1 => any2ArrowAssoc(1) [46, 1]"
      )
      val actual = implicits(src, compiler)

      assertSameLists(expected, actual)
    }
  }

  @Test
  def implicitArguments() {
    withCompilationUnitAndCompiler("implicit-highlighting/ImplicitArguments.scala") {(src, compiler) =>

      val expected = List (
        "Implicit arguments found: takesImplArg => takesImplArg( implicits.ImplicitArguments.s ) [118, 12]"
      )
      val actual = implicits(src, compiler)

      assertSameLists(expected, actual)
    }
  }

  def withCompilationUnitAndCompiler(path: String)(test: (ScalaPresentationCompiler, ScalaCompilationUnit) => Unit) {
    import ImplicitsHighlightingTest._

    val unit = scalaCompilationUnit(path)

    project.withSourceFile(unit) { (src, compiler) =>
      val dummy = new Response[Unit]
      compiler.askReload(List(src), dummy)
      dummy.get

      val tree =  new Response[compiler.Tree]
      compiler.askType(src, false, tree)
      tree.get
      test(compiler, unit)
    }()
  }

  def implicits(compiler: ScalaPresentationCompiler, scu: ScalaCompilationUnit) = {
    val implicits = ImplicitHighlightingPresenter.findAllImplicitConversions(compiler, scu, scu.sourceFile())
    implicits.toList map {
      case (ann, p) =>
        ann.getText() +" ["+ p.getOffset() + ", "+ p.getLength() +"]"
    } sortBy identity
  }

  def assertSameLists(l1: List[String], l2: List[String]) {
    Assert.assertEquals(l1.mkString("\n"), l2.mkString("\n"))
  }
}