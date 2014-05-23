package org.scalaide.util.internal.eclipse

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.core.runtime.IPath
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.ui.PlatformUI

object ProjectUtils {

  /**
   * Returns the associated `IJavaProject` to a given `IProject` if there
   * exists one and if the project is not closed. Returns `None` otherwise.
   */
  def projectAsJavaProject(p: IProject): Option[IJavaProject] =
    if (p.isOpen() && p.hasNature(JavaCore.NATURE_ID))
      Some(JavaCore.create(p))
    else
      None

  /**
   * Returns all source folders that exist in a given project. Note that only
   * Java projects are scanned, because only they are guaranteed to support the
   * concept of source folders.
   */
  def sourceDirs(p: IProject): Seq[IPath] =
    projectAsJavaProject(p) map { p =>
      val cs = p.getResolvedClasspath(/*ignoreUnresolvedEntry*/ true)
      cs.iterator.filter(_.getContentKind() == IPackageFragmentRoot.K_SOURCE).map(_.getPath()).toList
    } getOrElse Nil

  /**
   * Returns the resource of the current selection if it can be represented as a
   * resource. This is the case when either an editor is focused or when a view
   * is focused whose elements represent existing resources on the filesystem.
   *
   * `None` is returned otherwise.
   */
  def resourceOfSelection(): Option[IResource] = {
    val w = PlatformUI.getWorkbench().getActiveWorkbenchWindow()

    w.getSelectionService().getSelection() match {
      case s: ITextSelection =>
        val ei = w.getActivePage().getActiveEditor().getEditorInput()
        Option(ei.getAdapter(classOf[IResource]).asInstanceOf[IResource])

      case s: IStructuredSelection =>
        s.getFirstElement() match {
          case a: IAdaptable =>
            Option(a.getAdapter(classOf[IResource]).asInstanceOf[IResource])
          case _ =>
            None
        }

      case _ =>
        None
    }
  }
}