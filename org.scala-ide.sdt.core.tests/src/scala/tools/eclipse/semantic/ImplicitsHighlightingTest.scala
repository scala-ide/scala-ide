package scala.tools.eclipse.semantic

import org.junit.Test
import org.junit.Assert

import scala.collection.JavaConversions.mapAsScalaMap
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.semantic.highlighting.SemanticHighlightingPresenter
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.nsc.interactive.Response
import scala.tools.nsc.util.SourceFile

object ImplicitsHighlightingTest extends TestProjectSetup("implicits-highlighting")

class ImplicitsHighlightingTest {

  @Test 
  def implicitConversion() {
    withSourceFileAndCompiler("implicit-highlighting/Implicits.scala") { (src, compiler) =>
      
      val expected = List(
        "Implicit conversions found: List(1,2) => listToString(List(1,2)) [184, 9]",
        "Implicit conversions found: List(1,2,3) => listToString(List(1,2,3)) [153, 11]"
      )
      val actual = implicits(src, compiler)

      assertSameLists(expected, actual)
    }
  }
  
  @Test 
  def implicitConversionsFromPredef() {
    withSourceFileAndCompiler("implicit-highlighting/DefaultImplicits.scala") { (src, compiler) =>
 
      val expected = List(
        "Implicit conversions found: 1 => any2ArrowAssoc(1) [46, 1]"
      )
      val actual = implicits(src, compiler)
      
      assertSameLists(expected, actual)
    }
  }
  
  @Test 
  def implicitArguments() {
    withSourceFileAndCompiler("implicit-highlighting/ImplicitArguments.scala") {(src, compiler) =>
           
      val expected = List (
        "Implicit arguments found: takesImplArg => takesImplArg( s ) [124, 12]"
      )
      val actual = implicits(src, compiler)

      assertSameLists(expected, actual) 
    }
  }
        
  def withSourceFileAndCompiler(path: String)(test: (SourceFile, ScalaPresentationCompiler) => Unit) {
    import ImplicitsHighlightingTest._
    
    val unit = compilationUnit(path).asInstanceOf[ScalaSourceFile]
    
    project.withSourceFile(unit) { (src, compiler) =>
      val dummy = new Response[Unit]
      compiler.askReload(List(src), dummy)
      dummy.get
      
      val tree =  new Response[compiler.Tree]
      compiler.askType(src, false, tree)
      tree.get      
      test(src, compiler)
    }()
  }
  
  def implicits(src: SourceFile, compiler: ScalaPresentationCompiler) = {
    val implicits = SemanticHighlightingPresenter.findAllImplicitConversions(compiler, src)
    implicits.toList map {
      case (ann, p) =>
        ann.getText() +" ["+ p.getOffset() + ", "+ p.getLength() +"]"
    } sortBy identity
  }

  def assertSameLists(l1: List[String], l2: List[String]) {
    Assert.assertEquals(l1.mkString("\n"), l2.mkString("\n"))
  }
}