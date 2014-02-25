package org.scalaide.ui.internal.editor

import org.scalaide.core.compiler.InteractiveCompilationUnit

trait InteractiveCompilationUnitEditor extends DecoratedInteractiveEditor {
  def getInteractiveCompilationUnit(): InteractiveCompilationUnit
}
