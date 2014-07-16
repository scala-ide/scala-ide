package org.scalaide.util.internal.ui

import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.JavaModelException
import org.eclipse.jdt.internal.ui.wizards.TypedElementSelectionValidator
import org.eclipse.jdt.internal.ui.wizards.TypedViewerFilter
import org.eclipse.jdt.ui.JavaElementComparator
import org.eclipse.jdt.ui.JavaElementLabelProvider
import org.eclipse.jdt.ui.StandardJavaElementContentProvider
import org.eclipse.jface.viewers.Viewer
import org.eclipse.jface.window.Window
import org.eclipse.swt.widgets.Shell
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog
import org.scalaide.logging.HasLogger

object Dialogs extends HasLogger {

  /**
   * Opens a dialog, where all Java projects and their source folders that exist
   * in the workspace are listed. If one of the source folders is selected, it
   * is returned, otherwise `None` is returned.
   *
   * This dialog does not allow to select multiple projects.
   *
   * The implementation of this method is nearly an exact duplicate of
   * [[org.eclipse.jdt.ui.wizards.NewContainerWizardPage#chooseContainer]],
   * which is not static and therefore can only be used by direct subclasses.
   */
  def chooseSourceFolderWithDialog(shell: Shell): Option[IPackageFragmentRoot] = {
    val provider = new StandardJavaElementContentProvider()
    val labelProvider = new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT)
    val dialog = new ElementTreeSelectionDialog(shell, labelProvider, provider)

    def catching[A](default: A)(f: => A): A =
      try f catch {
        case e: JavaModelException =>
          eclipseLog.error("An error occurred while retrieving a Java model element.", e)
          default
      }

    val acceptedClasses = Array[Class[_]](classOf[IPackageFragmentRoot], classOf[IJavaProject])
    dialog.setValidator(new TypedElementSelectionValidator(acceptedClasses, false) {
      override def isSelectedValid(elem: AnyRef) = catching(false) {
        elem match {
          case p: IJavaProject =>
            val path = p.getProject().getFullPath()
            p.findPackageFragmentRoot(path) != null
          case p: IPackageFragmentRoot =>
            p.getKind() == IPackageFragmentRoot.K_SOURCE
          case _ =>
            false
        }
      }
    })
    dialog.setComparator(new JavaElementComparator)
    dialog.setTitle("Source Folder Selection")
    dialog.setMessage("Choose a source folder:")
    dialog.addFilter(new TypedViewerFilter(acceptedClasses) {
      override def select(viewer: Viewer, parent: AnyRef, elem: AnyRef) = catching(false) {
        elem match {
          case p: IPackageFragmentRoot =>
            p.getKind() == IPackageFragmentRoot.K_SOURCE
          case _ =>
            super.select(viewer, parent, elem)
        }
      }
    })
    dialog.setInput(JavaCore.create(ResourcesPlugin.getWorkspace().getRoot()))
    dialog.setHelpAvailable(false)
    dialog.setAllowMultiple(false)

    if (dialog.open() == Window.OK)
      Some(dialog.getFirstResult().asInstanceOf[IPackageFragmentRoot])
    else
      None
  }
}