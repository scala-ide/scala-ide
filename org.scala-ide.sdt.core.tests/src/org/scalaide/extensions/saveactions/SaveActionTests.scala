package org.scalaide.extensions
package saveactions

import scala.reflect.internal.util.SourceFile
import org.eclipse.jface.text.{Document => EDocument}
import org.scalaide.CompilerSupportTests
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
import org.scalaide.core.internal.text.TextDocument
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.text.Document
import org.scalaide.core.text.TextChange
import org.scalaide.core.ui.TextEditTests
import org.scalaide.extensions.SaveAction
import org.scalaide.util.eclipse.EclipseUtils
import org.scalaide.core.text.Change

abstract class SaveActionTests extends TextEditTests {

  final def applyChanges(udoc: EDocument, changes: Seq[Change]): Unit = {
    val sorted = changes.sortBy {
      case TextChange(start, _, _) => -start
    }
    sorted foreach {
      case TextChange(start, end, text) =>
        udoc.replace(start, end-start, text)
    }
  }
}

abstract class DocumentSaveActionTests extends SaveActionTests {

  def saveAction(doc: Document): SaveAction

  private var udoc: EDocument = _

  override def runTest(source: String, operation: Operation): Unit = {
    udoc = new EDocument(source)
    operation.execute()
  }

  override def source: String = udoc.get()

  case object SaveEvent extends Operation {
    override def execute: Unit = {
      val changes = saveAction(new TextDocument(udoc)).perform()
      applyChanges(udoc, changes)
    }
  }
}

abstract class CompilerSaveActionTests extends SaveActionTests with CompilerSupportTests {

  def saveAction(spc: IScalaPresentationCompiler, tree: IScalaPresentationCompiler#Tree, sf: SourceFile, selectionStart: Int, selectionEnd: Int): SaveAction

  private var udoc: EDocument = _

  override def runTest(source: String, operation: Operation): Unit = {
    udoc = new EDocument(source)
    EclipseUtils.workspaceRunnableIn(SDTTestUtils.workspace) { _ =>
      operation.execute()
    }
  }

  override def source: String = udoc.get()

  case object SaveEvent extends Operation {
    override def execute: Unit = withCompiler { compiler =>
      import compiler._

      val unit = mkScalaCompilationUnit(udoc.get())
      val sf = unit.lastSourceMap().sourceFile
      val r = askLoadedTyped(sf, false)

      r.get match {
        case Left(tree) =>
          val ps = unit.currentProblems()
          if (ps.nonEmpty)
            throw new IllegalArgumentException(s"Got compilation errors:\n\t${ps.mkString("\n\t")}")

          val sa = saveAction(compiler, tree, sf, 0, 0)
          val changes = compiler.asyncExec(sa.perform()).getOrElse(Seq())()
          applyChanges(udoc, changes)
        case Right(e) =>
          throw e
      }
    }
  }
}
