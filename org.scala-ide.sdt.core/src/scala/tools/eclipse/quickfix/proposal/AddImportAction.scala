package scala.tools.eclipse.quickfix.proposal

import org.eclipse.core.commands.{ AbstractHandler, ExecutionEvent }
import org.eclipse.jface.action.{ Action, AbstractAction, IAction }
import org.eclipse.jface.util.IPropertyChangeListener
import scala.tools.eclipse.refactoring.ActionAdapter

/**
 * Dummy noop action for now to suppress errors (see ticket #2961) -- but see ImportCompletionProposal
 */
class AddImportAction extends ActionAdapter {

  def run(action: IAction) {}
}

