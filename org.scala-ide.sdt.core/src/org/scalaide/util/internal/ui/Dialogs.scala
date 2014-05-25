package org.scalaide.util.internal.ui

import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.internal.ui.wizards.TypedViewerFilter
import org.eclipse.jdt.ui.JavaElementComparator
import org.eclipse.jdt.ui.JavaElementLabelProvider
import org.eclipse.jdt.ui.StandardJavaElementContentProvider
import org.eclipse.jface.window.Window
import org.eclipse.swt.widgets.Shell
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog

object Dialogs {

  /**
   * Opens a dialog, where all Java projects that exist in the workspace are
   * listed. If one of these projects is selected, it is returned, otherwise
   * `None` is returned.
   *
   * This dialog does not allow to select multiple projects.
   */
  def chooseProjectWithDialog(shell: Shell): Option[IJavaProject] = {
    val provider = new StandardJavaElementContentProvider()
    val labelProvider = new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT)
    val dialog = new ElementTreeSelectionDialog(shell, labelProvider, provider)

    dialog.setComparator(new JavaElementComparator)
    dialog.setTitle("Project Selection")
    dialog.setMessage("Choose a project:")
    dialog.addFilter(new TypedViewerFilter(Array(classOf[IJavaProject])))
    dialog.setInput(JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()))
    dialog.setHelpAvailable(false)
    dialog.setAllowMultiple(false)

    if (dialog.open() == Window.OK)
      Some(dialog.getFirstResult().asInstanceOf[IJavaProject])
    else
      None
  }
}