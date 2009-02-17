/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor;

import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.widgets.Composite;

@SuppressWarnings("restriction")
public privileged aspect ScalaCloseStringsAspect {
  pointcut setCloseStringsEnabled(boolean enabled) :
    args(enabled) &&
    execution(void CompilationUnitEditor.BracketInserter.setCloseStringsEnabled(boolean));
  
  pointcut invocations(ScalaEditor editor) :
    target(editor) &&
    (execution(void CompilationUnitEditor.createPartControl(Composite)) ||
     execution(void CompilationUnitEditor.handlePreferenceStoreChanged(PropertyChangeEvent)));
  
  void around(ScalaEditor editor, boolean enabled) :
    setCloseStringsEnabled(enabled) && cflow(invocations(editor)) {
    proceed(editor, false);
  }
}
