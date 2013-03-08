package scala.tools.eclipse.quickfix

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.contentassist.IContextInformation
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point
import scala.tools.eclipse.refactoring.ExtractLocalAction
import scala.tools.eclipse.refactoring.InlineLocalAction

object InlineLocalProposal
  extends ProposalRefactoringActionAdapter(
      new InlineLocalAction, "Inline local value")