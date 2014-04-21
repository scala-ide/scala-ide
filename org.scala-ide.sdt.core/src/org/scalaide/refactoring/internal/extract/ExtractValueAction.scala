package org.scalaide.refactoring.internal.extract

import scala.tools.refactoring.common.InteractiveScalaCompiler
import scala.tools.refactoring.implementations.extraction.ExtractValue

import org.scalaide.core.internal.jdt.model.ScalaSourceFile

class ExtractValueAction extends ExtractAction {
  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) =
    new ScalaIdeExtractionRefactoring(selectionStart, selectionEnd, file) {
      val refactoring = file.withSourceFile { (sourceFile, compiler) =>
        new ExtractValue with InteractiveScalaCompiler {
          val global = compiler
        }
      }.get
    }
}