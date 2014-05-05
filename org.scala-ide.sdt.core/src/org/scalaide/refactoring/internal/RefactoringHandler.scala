package org.scalaide.refactoring.internal

import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent

/**
 * Entry point of all refactorings that are executed through menu interaction
 * in the IDE.
 */
trait RefactoringHandler extends AbstractHandler {

  final override def execute(event: ExecutionEvent): Object = {
    perform()
    null
  }

  /**
   * This method is called when a menu point is applied, therefore it should
   * execute the concrete refactoring.
   */
  def perform(): Unit
}