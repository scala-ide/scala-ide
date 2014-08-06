package org.scalaide.core.internal.extensions.saveactions

import org.scalaide.core.compiler.ScalaPresentationCompiler
import scala.reflect.internal.util.SourceFile
import org.scalaide.extensions.CompilerSupport
import org.scalaide.extensions.saveactions.AddMissingOverride

object AddMissingOverrideCreator {
  def create(
      c: ScalaPresentationCompiler,
      t: ScalaPresentationCompiler#Tree,
      sf: SourceFile,
      selectionStart: Int,
      selectionEnd: Int): AddMissingOverride =
    new AddMissingOverride {
      override val global = c
      override val sourceFile = sf
      override val selection = new FileSelection(
          sf.file, t.asInstanceOf[global.Tree], selectionStart, selectionEnd)
    }
}