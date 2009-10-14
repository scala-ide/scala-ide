/*
 * Copyright 2005-2009 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor;

import org.eclipse.jdt.core.ICodeAssist;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.ui.text.java.hover.AbstractJavaEditorTextHover;
import org.eclipse.jdt.internal.ui.viewsupport.JavaElementLabelComposer;
import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextViewer;

import scala.tools.eclipse.contribution.weaving.jdt.IScalaCompilationUnit;
import scala.tools.eclipse.contribution.weaving.jdt.IScalaElement;

@SuppressWarnings("restriction")
public privileged aspect HoversAspect {
  pointcut getHoverRegion(AbstractJavaEditorTextHover th, ITextViewer textViewer, int offset) :
    execution(IRegion AbstractJavaEditorTextHover.getHoverRegion(ITextViewer, int)) &&
    args(textViewer, offset) &&
    target(th);
  
  pointcut appendElementLabel(JavaElementLabelComposer jelc, IJavaElement element, long flags) :
    execution(void JavaElementLabelComposer.appendElementLabel(IJavaElement, long)) &&
    args(element, flags) &&
    target(jelc);
  
  IRegion around(AbstractJavaEditorTextHover th, ITextViewer textViewer, int offset) :
    getHoverRegion(th, textViewer, offset) {
    ICodeAssist codeAssist = th.getCodeAssist(); 
  
    if (!(codeAssist instanceof IScalaCompilationUnit))
      return proceed(th, textViewer, offset);
    
    IScalaCompilationUnit scu = (IScalaCompilationUnit)codeAssist;
    return scu.getScalaWordFinder().findWord(textViewer.getDocument(), offset);
  }

  void around(JavaElementLabelComposer jelc, IJavaElement element, long flags) :
    appendElementLabel(jelc, element, flags) {
    if (!(element instanceof IScalaElement))
      proceed(jelc, element, flags);
    else {
      IPackageFragmentRoot root = JavaModelUtil.getPackageFragmentRoot(element);
      if (JavaElementLabelComposer.getFlag(flags, JavaElementLabels.PREPEND_ROOT_PATH)) {
        jelc.appendPackageFragmentRootLabel(root, JavaElementLabels.ROOT_QUALIFIED);
        jelc.fBuffer.append(JavaElementLabels.CONCAT_STRING);
      }
      
      IScalaElement scalaElement = (IScalaElement)element;
      jelc.fBuffer.append(scalaElement.getLabelText(flags));
      
      if (JavaElementLabelComposer.getFlag(flags, JavaElementLabels.APPEND_ROOT_PATH)) {
        int offset= jelc.fBuffer.length();
        jelc.fBuffer.append(JavaElementLabels.CONCAT_STRING);
        jelc.appendPackageFragmentRootLabel(root, JavaElementLabels.ROOT_QUALIFIED);

        if (JavaElementLabelComposer.getFlag(flags, JavaElementLabels.COLORIZE)) {
          jelc.fBuffer.setStyle(offset, jelc.fBuffer.length() - offset, JavaElementLabelComposer.QUALIFIER_STYLE);
        }
      }
    }
  }
}
