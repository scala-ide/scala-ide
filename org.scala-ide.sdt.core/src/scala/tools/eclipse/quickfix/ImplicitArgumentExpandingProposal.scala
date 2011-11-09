package scala.tools.eclipse
package quickfix

import org.eclipse.jface.text.Position

class ImplicitArgumentExpandingProposal(s: String, pos: Position) 
  extends ExpandingProposalBase(s, "Explicitly inline the implicit arguments: ", pos)