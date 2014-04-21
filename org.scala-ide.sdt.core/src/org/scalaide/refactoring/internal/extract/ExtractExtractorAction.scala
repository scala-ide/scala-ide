package org.scalaide.refactoring.internal.extract

import scala.tools.refactoring.common.InteractiveScalaCompiler
import scala.tools.refactoring.implementations.extraction.ExtractExtractor

import org.scalaide.core.internal.jdt.model.ScalaSourceFile

class ExtractExtractorAction extends ExtractAction {
  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) =
    new ScalaIdeExtractionRefactoring(selectionStart, selectionEnd, file) {
      val refactoring = file.withSourceFile { (sourceFile, compiler) =>
        new ExtractExtractor with InteractiveScalaCompiler {
          val global = compiler
        }
      }.get

      override val preferredName = "Extracted"

      def refactoringParameters =
        selectedExtraction.get
          .withAbstractionName(proposedPlaceholderName)
    }
}