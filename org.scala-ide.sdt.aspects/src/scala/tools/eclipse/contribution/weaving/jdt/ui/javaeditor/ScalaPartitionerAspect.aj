package scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor;

import org.eclipse.jdt.ui.text.JavaTextTools;
import org.eclipse.jface.text.rules.IPartitionTokenScanner;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.texteditor.AbstractTextEditor;

public aspect ScalaPartitionerAspect {
  pointcut getPartitionScanner() :
    call(IPartitionTokenScanner JavaTextTools.getPartitionScanner());
  
  pointcut doSetInput(AbstractTextEditor editor) :
    execution(void AbstractTextEditor.doSetInput(IEditorInput)) &&
    target(editor);
  
  IPartitionTokenScanner around(AbstractTextEditor editor) :
    getPartitionScanner() &&
    cflow(doSetInput(editor)) {
    if (editor instanceof IScalaEditor)
      return ((IScalaEditor)editor).getPartitionScanner();
    else
      return proceed(editor);
  }
}
