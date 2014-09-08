package org.scalaide.core.internal.extensions.saveactions

import scala.reflect.internal.util.SourceFile

import org.scalaide.core.compiler.ScalaPresentationCompiler
import org.scalaide.extensions.saveactions.AddReturnTypeToPublicSymbols

object AddReturnTypeToPublicSymbolsCreator {
  def create(
      c: ScalaPresentationCompiler,
      t: ScalaPresentationCompiler#Tree,
      sf: SourceFile,
      selectionStart: Int,
      selectionEnd: Int): AddReturnTypeToPublicSymbols =
    new AddReturnTypeToPublicSymbols {
      override val global = c
      override val sourceFile = sf
      override val selection = new FileSelection(
          sf.file, t.asInstanceOf[global.Tree], selectionStart, selectionEnd)
    }
}