package org.scalaide.ui.editor

import org.scalaide.core.compiler.InteractiveCompilationUnit

trait InteractiveCompilationUnitEditor extends DecoratedInteractiveEditor {
  /** Returns `null` if the editor is closed. */
  def getInteractiveCompilationUnit(): InteractiveCompilationUnit
}
