package org.scalaide.ui.internal.wizards

import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.swt.widgets.Display

class WizardHandler extends AbstractHandler {

  private val DefaultTypeCreator = "org.scalaide.ui.wizards.classCreator"

  override def execute(e: ExecutionEvent): AnyRef = {
    val d = new NewFileWizard(Display.getCurrent().getActiveShell(), DefaultTypeCreator)
    d.open()
    null
  }
}