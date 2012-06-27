package scala.tools.eclipse.ui

import org.eclipse.ui.IPartListener
import org.eclipse.ui.IWorkbenchPart

trait PartAdapter extends IPartListener {
  override def partActivated(part: IWorkbenchPart): Unit = {}
  override def partDeactivated(part: IWorkbenchPart): Unit = {}
  override def partBroughtToTop(part: IWorkbenchPart): Unit = {}
  override def partOpened(part: IWorkbenchPart): Unit = {}
  override def partClosed(part: IWorkbenchPart): Unit = {}
}