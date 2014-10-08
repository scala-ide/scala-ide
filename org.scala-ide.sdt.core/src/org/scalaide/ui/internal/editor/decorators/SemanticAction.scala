package org.scalaide.ui.internal.editor.decorators

import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit

trait SemanticAction extends (ScalaCompilationUnit => Unit) {
  def apply(scu: ScalaCompilationUnit): Unit
}
