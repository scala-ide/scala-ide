package scala.tools.eclipse.semantic

import scala.tools.eclipse.javaelements.ScalaCompilationUnit

trait SemanticAction {
  def update(scu: ScalaCompilationUnit): Unit
}