package org.scalaide.core.internal.quickfix

import org.eclipse.jface.text.Position

class ImplicitConversionExpandingProposal(s: String, pos: Position)
  extends ExpandingProposalBase(s, "Expand this implicit conversion: ", pos)
