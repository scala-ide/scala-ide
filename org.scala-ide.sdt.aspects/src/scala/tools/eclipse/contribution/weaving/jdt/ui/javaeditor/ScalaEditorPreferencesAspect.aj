/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.swt.SWT;

@SuppressWarnings("restriction")
public privileged aspect ScalaEditorPreferencesAspect {
  pointcut isFoldingEnabled() :
    execution(boolean JavaEditor.isFoldingEnabled());

  pointcut isSemanticHighlightingEnabled() :
    execution(boolean JavaEditor.isSemanticHighlightingEnabled());
  
  pointcut handlePreferenceStoreChanged(PropertyChangeEvent event) :
    args(event) && execution(void CompilationUnitEditor.handlePreferenceStoreChanged(PropertyChangeEvent));

  boolean around(ScalaEditor editor) :
    isFoldingEnabled() && target(editor) {
    // Folding not yet supported for Scala
    return false;
  }
  
  boolean around(ScalaEditor editor) :
    isSemanticHighlightingEnabled() && target(editor) {
    // Disable Java semantic highlighting for Scala source
    return false;
  }
  
  void around(ScalaEditor editor, PropertyChangeEvent event) :
    handlePreferenceStoreChanged(event) && target(editor) {
    editor.handlePreferenceStoreChanged0(event);
  }
  
  public void CompilationUnitEditor.handlePreferenceStoreChanged0(PropertyChangeEvent event) {
    try {
      String p= event.getProperty();

      if (CLOSE_BRACKETS.equals(p)) {
        fBracketInserter.setCloseBracketsEnabled(getPreferenceStore().getBoolean(p));
        return;
      }

      if (CLOSE_STRINGS.equals(p)) {
        fBracketInserter.setCloseStringsEnabled(getPreferenceStore().getBoolean(p));
        return;
      }

      if (JavaCore.COMPILER_SOURCE.equals(p)) {
        boolean closeAngularBrackets= JavaCore.VERSION_1_5.compareTo(getPreferenceStore().getString(p)) <= 0;
        fBracketInserter.setCloseAngularBracketsEnabled(closeAngularBrackets);
      }

      if (SPACES_FOR_TABS.equals(p)) {
        if (isTabsToSpacesConversionEnabled())
          installTabsToSpacesConverter();
        else
          uninstallTabsToSpacesConverter();
        return;
      }

      if (PreferenceConstants.EDITOR_SMART_TAB.equals(p)) {
        if (getPreferenceStore().getBoolean(PreferenceConstants.EDITOR_SMART_TAB)) {
          setActionActivationCode("IndentOnTab", '\t', -1, SWT.NONE); //$NON-NLS-1$
        } else {
          removeActionActivationCode("IndentOnTab"); //$NON-NLS-1$
        }
      }

      if (CODE_FORMATTER_TAB_SIZE.equals(p) && isTabsToSpacesConversionEnabled()) {
        uninstallTabsToSpacesConverter();
        installTabsToSpacesConverter();
      }
    } finally {
      super.handlePreferenceStoreChanged(event);
    }
  }
}
