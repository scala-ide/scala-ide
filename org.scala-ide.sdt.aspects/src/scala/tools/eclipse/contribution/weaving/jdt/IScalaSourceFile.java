/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt;

import org.eclipse.jdt.core.compiler.IProblem;

public interface IScalaSourceFile {
  public IProblem[] getProblems();
}
