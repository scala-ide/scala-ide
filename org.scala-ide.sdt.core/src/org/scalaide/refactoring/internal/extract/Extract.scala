package org.scalaide.refactoring.internal.extract

import scala.tools.refactoring.common.InteractiveScalaCompiler
import scala.tools.refactoring.implementations.extraction.ExtractCode

import org.scalaide.core.internal.jdt.model.ScalaSourceFile

class Extract extends ExtractionExecutor {
  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) =
    new ScalaIdeExtractionRefactoring(selectionStart, selectionEnd, file) {
      val refactoring = file.withSourceFile { (sourceFile, compiler) =>
        new ExtractCode with InteractiveScalaCompiler {
          val global = compiler
        }
      }.get
    }
}