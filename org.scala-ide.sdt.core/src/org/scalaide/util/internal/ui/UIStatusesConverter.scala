package org.scalaide.util.internal.ui

import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.core.runtime.IStatus

object UIStatusesConverter{

  def MessageDialogOfIStatus(statusSeverity: Int): Int = statusSeverity match {
    case IStatus.OK | IStatus.INFO => MessageDialog.NONE
    case IStatus.WARNING => MessageDialog.WARNING
    case IStatus.ERROR | IStatus.CANCEL => MessageDialog.ERROR
  }

}