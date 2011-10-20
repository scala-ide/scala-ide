package scala.tools.eclipse.util

import scala.collection.JavaConversions._

import org.eclipse.core.resources._
import org.eclipse.core.runtime._
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.text.edits.{ ReplaceEdit, TextEdit => EclipseTextEdit }
import org.eclipse.ui.ide.IDE

import scalariform.utils.TextEdit

object EclipseUtils {

  implicit def adaptableToPimpedAdaptable(adaptable: IAdaptable): PimpedAdaptable = new PimpedAdaptable(adaptable)

  class PimpedAdaptable(adaptable: IAdaptable) {

    def adaptTo[T](implicit m: Manifest[T]): T = adaptable.getAdapter(m.erasure).asInstanceOf[T]

    def adaptToSafe[T](implicit m: Manifest[T]): Option[T] = Option(adaptable.getAdapter(m.erasure).asInstanceOf[T])

  }

  implicit def documentToPimpedDocument(document: IDocument): PimpedDocument = new PimpedDocument(document)

  class PimpedDocument(document: IDocument) {

    def apply(offset: Int): Char = document.getChar(offset)

  }

  implicit def asEclipseTextEdit(edit: TextEdit): EclipseTextEdit =
    new ReplaceEdit(edit.position, edit.length, edit.replacement)

  /** Run the given function as a workspace runnable inside `wspace`.
   *
   * @param wspace the workspace
   * @param monitor the progress monitor (defaults to null for no progress monitor).
   */
  def workspaceRunnableIn(wspace: IWorkspace, monitor: IProgressMonitor = null)(f: IProgressMonitor => Unit) = {
    wspace.run(new IWorkspaceRunnable {
      def run(monitor: IProgressMonitor) {
        f(monitor)
      }
    }, monitor)
  }

  def computeSelectedResources(selection: IStructuredSelection): List[IResource] =
    IDE.computeSelectedResources(selection).toList.asInstanceOf[List[IResource]]

}