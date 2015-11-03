package org.scalaide.core.internal.quickassist
package extract

import scala.collection.mutable.ArrayBuffer
import scala.reflect.internal.util.SourceFile
import scala.tools.refactoring.common.InteractiveScalaCompiler
import scala.tools.refactoring.common.TextChange
import scala.tools.refactoring.implementations.extraction.ExtractCode

import org.eclipse.jface.text.IDocument
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist
import org.scalaide.refactoring.internal.extract.ExtractionProposal
import org.scalaide.refactoring.internal.extract.LocalNameOccurrences
import org.scalaide.util.eclipse.EditorUtils
import org.scalaide.util.internal.eclipse.TextEditUtils

class ExtractExpressions extends QuickAssist {
  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] =
    quickAssistProposals(ctx.icu, ctx.selectionStart, ctx.selectionStart+ctx.selectionLength)

  private def quickAssistProposals(icu: InteractiveCompilationUnit, selectionStart: Int, selectionEnd: Int): Seq[ExtractionProposal] = {
    val proposals = new ArrayBuffer[ExtractionProposal]

    icu.withSourceFile { (file, compiler) =>
      val refactoring = createRefactoring(compiler, file, selectionStart, selectionEnd)
      var relevance = RelevanceValues.ProposalRefactoringHandlerAdapter - 1

      refactoring.extractions.foreach { extraction =>
        val pos = extraction.extractionTarget.enclosing.pos

        proposals += new ExtractionProposal(extraction.displayName, pos.start, pos.end, relevance) {
          override def applyProposal(doc: IDocument) = {
            refactoring.perform(extraction) match {
              case Right((change: TextChange) :: Nil) =>
                EditorUtils.doWithCurrentEditor { editor =>
                  TextEditUtils.applyRefactoringChangeToEditor(change, editor)
                  LocalNameOccurrences(extraction.abstractionName).performInlineRenaming()
                }
            }
          }
        }

        relevance -= 1
      }
    }

    proposals
  }

  private def createRefactoring(compiler: IScalaPresentationCompiler, file: SourceFile, selectionStart: Int, selectionEnd: Int) =
    new ExtractCode with InteractiveScalaCompiler {
      override val global = compiler

      val selection =
        askLoadedAndTypedTreeForFile(file).fold(new FileSelection(file.file, _, selectionStart, selectionEnd), throw _)

      val extractions = prepare(selection).fold(_ => Nil, _.extractions)
    }
}
