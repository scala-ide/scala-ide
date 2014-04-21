package org.scalaide.refactoring.internal.extract

import scala.tools.refactoring.common.InteractiveScalaCompiler
import scala.tools.refactoring.implementations.extraction.ExtractMethod

import org.scalaide.core.internal.jdt.model.ScalaSourceFile

class ExtractMethodAction extends ExtractAction {
  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) =
    new ScalaIdeExtractionRefactoring(selectionStart, selectionEnd, file) {
      val refactoring = file.withSourceFile { (sourceFile, compiler) =>
        new ExtractMethod with InteractiveScalaCompiler {
          val global = compiler
        }
      }.get

      def refactoringParameters =
        selectedExtraction.get
          .withAbstractionName(proposedPlaceholderName)
    }
}