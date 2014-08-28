/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt;

import java.util.Map;

import org.eclipse.jdt.internal.core.search.matching.MatchLocator;
import org.eclipse.jdt.internal.core.search.matching.PossibleMatch;

@SuppressWarnings("restriction")
public interface IScalaCompilationUnit {
  public void reportMatches(MatchLocator matchLocator, PossibleMatch possibleMatch);
  public void createOverrideIndicators(Map annotationMap);
}
