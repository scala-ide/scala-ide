package org.scalaide.refactoring.internal.extract

import scala.collection.mutable.ArrayBuffer
import scala.reflect.internal.util.SourceFile
import scala.tools.refactoring.common.InteractiveScalaCompiler
import scala.tools.refactoring.common.TextChange
import scala.tools.refactoring.implementations.extraction.ExtractCode
import org.eclipse.core.resources.IMarker
import org.eclipse.jface.text.DocumentEvent
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.contentassist.ICompletionProposalExtension2
import org.eclipse.ui.IFileEditorInput
import org.scalaide.core.completion.RelevanceValues
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.util.eclipse.EditorUtils
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.util.internal.eclipse.TextEditUtils

abstract class ExtractionProposal(displayString: String, hightlightFrom: Int, highlightTo: Int, relevance: Int = 0)
  extends BasicCompletionProposal(relevance, displayString) with ICompletionProposalExtension2 {

  override def apply(doc: IDocument): Unit

  private var markerOpt: Option[IMarker] = None

  override def selected(viewer: ITextViewer, smartToggle: Boolean): Unit = {
    markerOpt.foreach(_.delete())
    EditorUtils.doWithCurrentEditor { editor =>
      markerOpt = editor.getEditorInput() match {
        case f: IFileEditorInput =>
          val m = f.getFile().createMarker("org.scalaide.refactoring.extractionScope")
          m.setAttribute(IMarker.CHAR_START, Integer.valueOf(hightlightFrom))
          m.setAttribute(IMarker.CHAR_END, Integer.valueOf(highlightTo))
          Some(m)
        case _ => None
      }
    }
  }

  override def unselected(viewer: ITextViewer): Unit = {
    markerOpt.foreach(_.delete())
  }

  override def apply(viewer: ITextViewer, trigger: Char, stateMask: Int, offset: Int): Unit = apply(null)
  override def validate(document: IDocument, offset: Int, event: DocumentEvent): Boolean = true
}

object ExtractionProposal {
  def getQuickAssistProposals(cu: ScalaCompilationUnit, selectionStart: Int, selectionEnd: Int): Array[ExtractionProposal] = {
    val proposals = new ArrayBuffer[ExtractionProposal]

    cu.withSourceFile { (file, compiler) =>
      val refactoring = createRefactoring(compiler, file, selectionStart, selectionEnd)
      var relevance = RelevanceValues.ProposalRefactoringHandlerAdapter - 1

      refactoring.extractions.foreach { extraction =>
        val pos = extraction.extractionTarget.enclosing.pos

        proposals += new ExtractionProposal(extraction.displayName, pos.start, pos.end, relevance) {
          override def apply(doc: IDocument) = {
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

    proposals.toArray
  }

  private def createRefactoring(compiler: IScalaPresentationCompiler, file: SourceFile, selectionStart: Int, selectionEnd: Int) =
    new ExtractCode with InteractiveScalaCompiler {
      override val global = compiler

      val selection =
        askLoadedAndTypedTreeForFile(file).fold(new FileSelection(file.file, _, selectionStart, selectionEnd), throw _)

      val extractions = prepare(selection).fold(_ => Nil, _.extractions)
    }
}
