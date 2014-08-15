package org.scalaide.extensions
package saveactions

import scala.reflect.internal.util.SourceFile

import org.eclipse.jface.text.{Document => EDocument}
import org.scalaide.CompilerSupportTests
import org.scalaide.core.compiler.ScalaPresentationCompiler
import org.scalaide.core.internal.text.TextDocument
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.core.ui.TextEditTests
import org.scalaide.extensions.SaveAction
import org.scalaide.util.internal.eclipse.EclipseUtils

abstract class DocumentSaveActionTests extends TextEditTests {

  def saveAction(doc: Document): SaveAction

  private var udoc: EDocument = _

  override def runTest(source: String, operation: Operation) = {
    udoc = new EDocument(source)
    operation.execute()
  }

  override def source = udoc.get()

  case object SaveEvent extends Operation {
    override def execute() = {
      val changes = saveAction(new TextDocument(udoc)).perform()
      val sorted = changes.sortBy {
        case TextChange(start, _, _) => -start
      }
      sorted foreach {
        case TextChange(start, end, text) =>
          udoc.replace(start, end-start, text)
      }
    }
  }
}

abstract class CompilerSaveActionTests extends TextEditTests with CompilerSupportTests {

  def saveAction(spc: ScalaPresentationCompiler, tree: ScalaPresentationCompiler#Tree, sf: SourceFile, selectionStart: Int, selectionEnd: Int): SaveAction

  private var udoc: EDocument = _

  override def runTest(source: String, operation: Operation) = {
    udoc = new EDocument(source)
    EclipseUtils.workspaceRunnableIn(SDTTestUtils.workspace) { _ =>
      operation.execute()
    }
  }

  override def source = udoc.get()

  case object SaveEvent extends Operation {
    override def execute() = withCompiler { compiler =>
      import compiler._

      val unit = mkScalaCompilationUnit(udoc.get())
      val sf = unit.sourceFile()
      val r = new Response[Tree]
      askLoadedTyped(sf, r)
      r.get match {
        case Left(tree) =>
          val sa = saveAction(compiler, tree, sf, 0, 0)
          val changes = compiler.askOption(() => sa.perform()).getOrElse(Seq())
          val sorted = changes.sortBy {
            case TextChange(start, _, _) => -start
          }
          sorted foreach {
            case TextChange(start, end, text) =>
              udoc.replace(start, end-start, text)
          }
        case Right(e) =>
          throw e
      }
    }
  }
}
