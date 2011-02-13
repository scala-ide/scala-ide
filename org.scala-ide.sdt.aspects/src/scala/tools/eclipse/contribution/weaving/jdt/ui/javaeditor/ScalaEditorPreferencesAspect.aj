/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor;

import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;

@SuppressWarnings("restriction")
public privileged aspect ScalaEditorPreferencesAspect {

  pointcut isSemanticHighlightingEnabled() :
    execution(boolean JavaEditor.isSemanticHighlightingEnabled());
  
  boolean around(IScalaEditor editor) :
    isSemanticHighlightingEnabled() && target(editor) {
    // Disable Java semantic highlighting for Scala source
    return false;
  }
}
