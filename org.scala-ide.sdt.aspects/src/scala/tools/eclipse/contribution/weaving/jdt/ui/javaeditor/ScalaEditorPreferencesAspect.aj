/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor;

import org.eclipse.jdt.internal.ui.text.correction.QuickAssistProcessor;
import org.eclipse.jdt.internal.ui.text.correction.AdvancedQuickAssistProcessor;
import org.eclipse.jdt.internal.ui.text.correction.QuickFixProcessor;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;

import scala.tools.eclipse.contribution.weaving.jdt.IScalaCompilationUnit;

@SuppressWarnings("restriction")
public privileged aspect ScalaEditorPreferencesAspect {

  pointcut isSemanticHighlightingEnabled() :
    execution(boolean JavaEditor.isSemanticHighlightingEnabled());
  
  boolean around(IScalaEditor editor) :
    isSemanticHighlightingEnabled() && target(editor) {
    // Disable Java semantic highlighting for Scala source
    return false;
  }

  pointcut getAssists(IInvocationContext context, IProblemLocation[] locations):
    execution(IJavaCompletionProposal[] QuickAssistProcessor.getAssists(IInvocationContext, IProblemLocation[])) && args(context, locations) ||
    execution(IJavaCompletionProposal[] AdvancedQuickAssistProcessor.getAssists(IInvocationContext, IProblemLocation[])) && args(context, locations) ||
    execution(IJavaCompletionProposal[] QuickFixProcessor.getCorrections(IInvocationContext, IProblemLocation[])) && args(context, locations);

  /**
   * Disable Java quick fixes/assists on Scala sources. They can be very slow, and totally useless.
   */
  IJavaCompletionProposal[] around(IInvocationContext context, IProblemLocation[] locations):
    getAssists(context, locations) {
      if (context.getCompilationUnit() instanceof IScalaCompilationUnit)
        return null;
      else
        return proceed(context, locations);
  }
}
