package scala.tools.eclipse

import org.eclipse.core.runtime.{ CoreException, IAdaptable }
import org.eclipse.debug.core.{ DebugPlugin, ILaunchConfiguration, ILaunchConfigurationType }
import org.eclipse.debug.ui.DebugUITools
import org.eclipse.jdt.core.{ IJavaElement, IMethod, IType }
import org.eclipse.jdt.internal.corext.SourceRange
import org.eclipse.jdt.internal.ui.IJavaHelpContextIds
import org.eclipse.jdt.internal.ui.javaeditor.selectionactions._
import org.eclipse.jdt.ui.actions.IJavaEditorActionDefinitionIds
import org.eclipse.jface.action.Action
import org.eclipse.jface.text.ITextSelection
import org.eclipse.ui.PlatformUI
import scala.tools.eclipse.javaelements.ScalaSourceFile

/**
 * A Scala-aware replacement for {@link org.eclipse.jdt.internal.ui.javaeditor.selectionactions.StructureSelectEnclosingAction}.
 *
 * The intention is to remove this and use default Java StructureSelectionAction's directly, but this requires implementing a 
 * more thorough mapping from Scala to the JDT DOM types.
 */
class ScalaStructureSelectEnclosingAction(editor: ScalaSourceFileEditor, selectionHistory: SelectionHistory) extends Action {

  override def run() {
    val selection = editor.getSelectionProvider.getSelection.asInstanceOf[ITextSelection]

    val scalaSourceFile = editor.getEditorInput.asInstanceOf[IAdaptable].getAdapter(classOf[IJavaElement]).asInstanceOf[ScalaSourceFile]
    scalaSourceFile.withSourceFile { (src, compiler) =>
      import compiler._
      val currentPos = rangePos(src, selection.getOffset, selection.getOffset, selection.getOffset + selection.getLength)

      object StrictlyContainingTreeTraverser extends Traverser {
        var containingPosOpt: Option[Position] = None
        override def traverse(t: Tree) {
          if (t.pos.includes(currentPos) && (t.pos.start < currentPos.start || t.pos.end > currentPos.end))
            containingPosOpt = Some(t.pos)
          super.traverse(t)
        }
      }

      val body = compiler.body(src)
      StrictlyContainingTreeTraverser.traverse(body)

      for (containingPos <- StrictlyContainingTreeTraverser.containingPosOpt) {
        selectionHistory.remember(new SourceRange(selection.getOffset, selection.getLength))
        val start = containingPos.start
        val end = containingPos.end
        try {
          selectionHistory.ignoreSelectionChanges()
          editor.selectAndReveal(start, end - start)
        } finally {
          selectionHistory.listenToSelectionChanges()
        }
      }
    }

  }

}
