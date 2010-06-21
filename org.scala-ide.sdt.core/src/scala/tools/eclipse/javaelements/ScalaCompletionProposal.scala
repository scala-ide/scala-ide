/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.javaelements

import org.eclipse.jdt.internal.codeassist.InternalCompletionProposal

import scala.tools.eclipse.contribution.weaving.jdt.IScalaCompletionProposal

class ScalaCompletionProposal(kind : Int, completionLocation : Int)
  extends InternalCompletionProposal(kind, completionLocation) with IScalaCompletionProposal {
  var suppressArgList = false
}
