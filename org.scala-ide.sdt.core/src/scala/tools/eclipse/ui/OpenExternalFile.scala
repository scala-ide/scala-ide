package scala.tools.eclipse.ui

import org.eclipse.core.filesystem.EFS
import org.eclipse.core.runtime.Path
import org.eclipse.ui.PartInitException
import org.eclipse.ui.ide.IDE
import java.io.File
import org.eclipse.swt.widgets.Listener
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.swt.widgets.Event
import org.eclipse.ui.PlatformUI

/** This class can be used to open an editor on a file outside the workspace. */
class OpenExternalFile private (file: File) extends Listener with HasLogger {
  require(file != null, "file should not be null.")
  require(file.exists, "file %s does not exist.".format(file.getAbsolutePath))

  import scala.util.control.Exception.catching

  def handleEvent(e: Event): Unit = open()

  def open(): Unit = {
    val parentLocation = file.getParent
    var fileStore = EFS.getLocalFileSystem().getStore(new Path(parentLocation));

    val fileName = file.getName
    fileStore = fileStore.getChild(fileName)
    if (!fileStore.fetchInfo().isDirectory() && fileStore.fetchInfo().exists()) {
      val wb = PlatformUI.getWorkbench
      val win = wb.getActiveWorkbenchWindow
      val page = win.getActivePage

      catching(classOf[PartInitException]) {
        // After opening the file, if any change occurs to the file a
        // popup will ask the user to refresh the resource.
        IDE.openInternalEditorOnFileStore(page, fileStore)
      }
    }
  }
}

object OpenExternalFile {
  def apply(file: File): OpenExternalFile = new OpenExternalFile(file)
}