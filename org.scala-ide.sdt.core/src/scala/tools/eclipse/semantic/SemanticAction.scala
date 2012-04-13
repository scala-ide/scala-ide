package scala.tools.eclipse.semantic

import scala.tools.eclipse.javaelements.ScalaCompilationUnit

trait SemanticAction extends Function1[ScalaCompilationUnit, Unit] {
  def apply(scu: ScalaCompilationUnit): Unit
}