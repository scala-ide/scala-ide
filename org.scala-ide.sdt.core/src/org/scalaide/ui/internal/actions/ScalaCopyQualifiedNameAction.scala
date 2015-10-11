package org.scalaide.ui.internal.actions

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds
import org.eclipse.jdt.internal.ui.JavaPluginImages
import org.eclipse.jdt.internal.ui.actions.ActionMessages
import org.eclipse.jdt.ui.actions.SelectionDispatchAction
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.swt.SWTError
import org.eclipse.swt.dnd.Clipboard
import org.eclipse.swt.dnd.DND
import org.eclipse.swt.dnd.TextTransfer
import org.eclipse.swt.dnd.Transfer
import org.eclipse.ui.PlatformUI
import org.scalaide.core.compiler.NamePrinter
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.core.internal.statistics.Features
import org.scalaide.ui.internal.editor.ScalaCompilationUnitEditor

/**
 * A Scala-aware replacement for CopyQualifiedNameAction.
 */
class ScalaCopyQualifiedNameAction(editor: ScalaCompilationUnitEditor) extends SelectionDispatchAction(editor.getSite) {
  initGui()

  override def run(): Unit = {
    ScalaPlugin().statistics.incUsageCounter(Features.CopyQualifiedName)
    val qname = {
      for (cu <- compilationUnit) yield {
        val selection = editor.getViewer.getSelectedRange
        val namePrinter = new NamePrinter(cu)
        namePrinter.qualifiedNameAt(selection.x)
      }
    }.flatten

    if (qname.isDefined)
      copyToClipboard(qname.get)
  }

  private def copyToClipboard(str: String): Unit = {
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

  private def initGui(): Unit = {
    setText(ActionMessages.CopyQualifiedNameAction_ActionName);
    setToolTipText(ActionMessages.CopyQualifiedNameAction_ToolTipText);
    setDisabledImageDescriptor(JavaPluginImages.DESC_DLCL_COPY_QUALIFIED_NAME);
    setImageDescriptor(JavaPluginImages.DESC_ELCL_COPY_QUALIFIED_NAME);
    PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.COPY_QUALIFIED_NAME_ACTION);
  }

  private def compilationUnit = Option(editor.getInteractiveCompilationUnit())
}
