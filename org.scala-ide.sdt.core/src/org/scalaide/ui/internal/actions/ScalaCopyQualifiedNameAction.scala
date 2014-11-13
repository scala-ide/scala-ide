package org.scalaide.ui.internal.actions

import org.eclipse.jface.action.Action
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.jdt.ui.actions.SelectionDispatchAction
import org.eclipse.ui.IWorkbenchSite
import org.eclipse.jdt.internal.ui.actions.CopyQualifiedNameAction
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitEditor
import scala.tools.eclipse.contribution.weaving.jdt.IScalaSourceFile
import org.scalaide.ui.internal.editor.ScalaCompilationUnitEditor
import org.eclipse.jface.text.Region
import org.scalaide.util.eclipse.RegionUtils
import org.eclipse.swt.dnd.Clipboard
import org.eclipse.swt.dnd.TextTransfer
import org.eclipse.swt.SWTError
import org.eclipse.swt.dnd.DND
import org.eclipse.jdt.internal.ui.actions.ActionMessages
import org.eclipse.swt.dnd.Transfer
import org.scalaide.core.compiler.NamePrinter

/**
 * A Scala-aware replacement for CopyQualifiedNameAction.
 */
class ScalaCopyQualifiedNameAction(editor: CompilationUnitEditor with ScalaCompilationUnitEditor) extends CopyQualifiedNameAction(editor) {

  override def run() {
    val qname = {
      for (cu <- compilationUnit) yield {
        val selection = editor.getViewer.getSelectedRange
        val namePrinter = new NamePrinter(cu)
        namePrinter.qualifiedNameAt(selection.x)
      }
    }.flatten

    if (qname.isDefined)
      copyToClipboard(qname.get)
    else
      super.run()
  }

  private def copyToClipboard(str: String) {
    val data: Array[Object] = Array(str)
    val dataTypes: Array[Transfer] = Array(TextTransfer.getInstance)
    val clipboard = new Clipboard(getShell().getDisplay());

    try {
      clipboard.setContents(data, dataTypes)
    } catch {
      case e: SWTError =>
        if (e.code != DND.ERROR_CANNOT_SET_CLIPBOARD) {
          throw e;
        }

        if (MessageDialog.openQuestion(getShell(), ActionMessages.CopyQualifiedNameAction_ErrorTitle, ActionMessages.CopyQualifiedNameAction_ErrorDescription)) {
          clipboard.setContents(data, dataTypes);
        }
    } finally {
      clipboard.dispose()
    }
  }

  private def compilationUnit = Option(editor.getInteractiveCompilationUnit())

  private def viewer = editor.getViewer
}
