package org.scalaide.core.pc

import scala.reflect.internal.util.Position
import scala.reflect.internal.util.SourceFile
import scala.tools.nsc.doc.base.comment.Comment

import org.junit.After
import org.junit.Assert
import org.junit.Test
import org.scalaide.core.FlakyTest
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits.RichResponse
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.testsetup.TestProjectSetup

object PresentationCompilerDocTest extends TestProjectSetup("pc_doc")

class PresentationCompilerDocTest {
  import PresentationCompilerDocTest._

  @After
  def tearDown(): Unit = project.presentationCompiler.shutdown()

  @Test
  def basicComment(): Unit = {
    val expect: Comment => Boolean = { cmt =>
      existsText(cmt.body, "This is a basic comment")
    }
    doTest(open("clasz.scala"), expect)
  }

  @Test
  def packagedComment(): Unit = {
    val expect: Comment => Boolean = { cmt =>
      existsText(cmt.body, "This is another basic comment")
    }
    doTest(open("packaged.scala"), expect)
  }

  @Test
  def parametricComment(): Unit = {
    val expect: Comment => Boolean = { cmt =>
      existsText(cmt.todo, "implement me")
    }
    doTest(open("parametric.scala"), expect)
  }

  @Test
  def variableExpansion(): Unit = {
    val expect: Comment => Boolean = { cmt =>
      existsText(cmt.body, "correctly got derived comment")
    }
    doTest(open("varz.scala"), expect)
  }

  @Test
  def inheritedDoc(): Unit = {
    val expect: Comment => Boolean = { cmt =>
      existsText(cmt.todo, "implement me")
    }
    doTest(open("inherited.scala"), expect)
  }

  @Test
  def inheritedTwoSourcesDoc() = FlakyTest.retry("inheritedTwoSourcesDoc", "") {
    val expect: Comment => Boolean = { cmt =>
      existsText(cmt.todo, "implement me")
    }
    doTest(open("inherit-2.scala"), expect, List(scalaCompilationUnit("inherit-1.scala")))
  }

  @Test
  def returnValueDoc(): Unit = {
    val expect: Comment => Boolean = { cmt =>
      existsText(cmt.result, "some value")
    }
    doTest(open("return.scala"), expect)
  }

  /**
   * @parameter preload compilation units expected to be loaded by the PC before the test
   * @parameter unit the compilation unit containing the position mark
   */
  private def doTest(unit: ScalaCompilationUnit, expectation: Comment => Boolean, preload: List[ScalaCompilationUnit] = Nil): Unit = {
    for (u <- preload) { reload(u) }
    unit.withSourceFile { (src, compiler) =>
      val pos = docPosition(src, compiler)
      val typ = compiler.askTypeAt(pos).getOption()
      typ match {
        case None => Assert.fail("Couldn't get typed tree")
        case Some(t) => val com = compiler.parsedDocComment(t.symbol, t.symbol.enclClass, unit.scalaProject.javaProject)
           com match {
            case None => Assert.fail("Couldn't get documentation for " + t.symbol)
            case Some(comment) => Assert.assertTrue(s"Expectation failed for $comment", expectation(comment))
          }
      }
    }
  }

  val rangeStartMarker = "/*s*/"
  val rangeEndMarker = "/*e*/"

  private def docPosition(src: SourceFile, compiler: IScalaPresentationCompiler): Position = {
    val content = new String(src.content)
    val start = content.indexOf(rangeStartMarker) + rangeStartMarker.length
    val end = content.indexOf(rangeEndMarker, start)
    compiler.rangePos(src, start, start, end)
  }

  private def existsText(where: Any, text: String): Boolean = where match {
    case s: String => s contains text
    case s: Seq[_] => s exists (existsText(_, text))
    case p: Product => p.productIterator exists (existsText(_, text))
  }
}
