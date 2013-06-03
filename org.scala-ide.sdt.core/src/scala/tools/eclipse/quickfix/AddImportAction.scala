package scala.tools.eclipse.quickfix

import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.jface.action.Action
import org.eclipse.jface.action.AbstractAction
import org.eclipse.jface.action.IAction
import org.eclipse.jface.util.IPropertyChangeListener
import scala.tools.eclipse.refactoring.ActionAdapter

/**
 * Dummy noop action for now to suppress errors (see ticket #2961) -- but see ImportCompletionProposal
 */
class AddImportAction extends ActionAdapter {

  def run(action: IAction) {}
}

