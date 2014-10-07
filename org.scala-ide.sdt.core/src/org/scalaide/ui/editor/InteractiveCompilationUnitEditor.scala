package org.scalaide.ui.editor

import org.scalaide.core.compiler.InteractiveCompilationUnit

trait InteractiveCompilationUnitEditor extends DecoratedInteractiveEditor {
  def getInteractiveCompilationUnit(): InteractiveCompilationUnit
}
