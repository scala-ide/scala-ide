package scala.tools.eclipse.ui

import scala.tools.eclipse.InteractiveCompilationUnit

trait InteractiveCompilationUnitEditor extends DecoratedInteractiveEditor {
  def getInteractiveCompilationUnit(): InteractiveCompilationUnit
}
