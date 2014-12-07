package org.scalaide.ui.internal.actions

import org.eclipse.jdt.internal.ui.actions.ActionMessages
import org.eclipse.jdt.internal.ui.actions.CopyQualifiedNameAction
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.swt.SWTError
import org.eclipse.swt.dnd.Clipboard
import org.eclipse.swt.dnd.DND
import org.eclipse.swt.dnd.TextTransfer
import org.eclipse.swt.dnd.Transfer
import org.scalaide.core.compiler.NamePrinter
import org.scalaide.ui.internal.editor.ScalaCompilationUnitEditor

/**
 * A Scala-aware replacement for CopyQualifiedNameAction.
 */
class ScalaCopyQualifiedNameAction(editor: ScalaCompilationUnitEditor) extends CopyQualifiedNameAction(editor) {

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
      callJavaImplementation()
  }

  private def callJavaImplementation() = super.run()

  private def copyToClipboard(str: String) {
    val data: Array[Object] = Array(str)
    val dataTypes: Array[Transfer] = Array(TextTransfer.getInstance)
    val clipboard = new Clipboard(getShell().getDisplay())

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
}
