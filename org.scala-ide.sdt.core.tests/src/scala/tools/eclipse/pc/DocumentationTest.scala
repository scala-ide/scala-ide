package scala.tools.eclipse
package pc

import javaelements.ScalaCompilationUnit
import scala.tools.nsc.doc.base.comment.Comment
import scala.tools.nsc.interactive.Response
import scala.reflect.internal.util.{ Position, SourceFile }
import org.junit._

object PresentationCompilerDocTest extends testsetup.TestProjectSetup("pc_doc")

class PresentationCompilerDocTest {
  import PresentationCompilerDocTest._

  @Test
  def variableExpansion() {
    val expect: Comment => Boolean = { cmt =>
      existsText(cmt.body, "correctly got derived comment")
    }
    doTest(open("varz.scala"), expect)
  }

  @Test
  def inheritedDoc() {
    val expect: Comment => Boolean = { cmt =>
      existsText(cmt.todo, "implement me")
    }
    doTest(open("inherited.scala"), expect)
  }

  private def doTest(unit: ScalaCompilationUnit, expectation: Comment => Boolean) {
    project.withSourceFile(unit) { (src, compiler) =>
      val pos = docPosition(src, compiler)
      val response = new Response[compiler.Tree]
      compiler.askTypeAt(pos, response)
      response.get.left.toOption match {
        case None => Assert.fail("Couldn't get typed tree")
        case Some(t) =>
          compiler.parsedDocComment(t.symbol, t.symbol.enclClass) match {
            case None => Assert.fail("Couldn't get documentation")
            case Some(comment) => Assert.assertTrue(s"Expectation failed for $comment", expectation(comment))
          }
      }
    }()
  }

  val rangeStartMarker = "/*s*/"
  val rangeEndMarker = "/*e*/"

  private def docPosition(src: SourceFile, compiler: ScalaPresentationCompiler): Position = {
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
