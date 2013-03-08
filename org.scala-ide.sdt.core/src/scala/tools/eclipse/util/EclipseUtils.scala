package scala.tools.eclipse.util

import scala.collection.JavaConversions._
import org.eclipse.core.resources._
import org.eclipse.core.runtime._
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.text.edits.{ ReplaceEdit, TextEdit => EclipseTextEdit }
import org.eclipse.ui.ide.IDE
import scalariform.utils.TextEdit
import org.eclipse.text.edits.{ TextEdit => EclipseTextEdit, _ }
import org.eclipse.jface.text.IDocumentExtension4
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.viewers.ISelection
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.core.resources.IWorkspace
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.resources.IWorkspaceRunnable
import scala.PartialFunction._
import scalariform.utils.TextEdit
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.swt.graphics.RGB
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.PlatformUI
import org.eclipse.jface.text.IRegion
import scala.tools.nsc.util.Position
import scala.tools.nsc.util.SourceFile
import scala.tools.nsc.interactive.RangePositions
import scala.tools.nsc.util.RangePosition
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.runtime.jobs.ISchedulingRule

object EclipseUtils {

  implicit def adaptableToPimpedAdaptable(adaptable: IAdaptable): PimpedAdaptable = new PimpedAdaptable(adaptable)

  class PimpedAdaptable(adaptable: IAdaptable) {

    def adaptTo[T](implicit m: Manifest[T]): T = adaptable.getAdapter(m.erasure).asInstanceOf[T]

    def adaptToSafe[T](implicit m: Manifest[T]): Option[T] = Option(adaptable.getAdapter(m.erasure).asInstanceOf[T])

  }

  implicit def prefStoreToPimpedPrefStore(preferenceStore: IPreferenceStore): PimpedPreferenceStore = new PimpedPreferenceStore(preferenceStore)

  class PimpedPreferenceStore(preferenceStore: IPreferenceStore) {

    def getColor(key: String): RGB = PreferenceConverter.getColor(preferenceStore, key)

  }

  implicit def documentToPimpedDocument(document: IDocument): PimpedDocument = new PimpedDocument(document)

  class PimpedDocument(document: IDocument) {

    def apply(offset: Int): Char = document.getChar(offset)

  }

  class PimpedRegion(region: IRegion) {
    def toRangePos(src: SourceFile): Position = {
      val offset = region.getOffset
      new RangePosition(src, offset, offset, offset + region.getLength)
    }
  }

  implicit def pimpedRegion(region: IRegion) = new PimpedRegion(region)

  implicit def asEclipseTextEdit(edit: TextEdit): EclipseTextEdit =
    new ReplaceEdit(edit.position, edit.length, edit.replacement)

  /**
   * Run the given function as a workspace runnable inside `wspace`.
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

  /**
   * Create a job with the given name. Default values for scheduling rules and priority are taken
   * from the `Job` implementation.
   *
   * @param rule The scheduling rule
   * @param priority The job priority (defaults to Job.LONG, like the platform `Job` class)
   * @return The job
   */
  def prepareJob(name: String, rule: ISchedulingRule = null, priority: Int = Job.LONG)(f: IProgressMonitor => IStatus): Job = {
    val job = new Job(name) {
      override def run(monitor: IProgressMonitor): IStatus = f(monitor)
    }
    job.setRule(rule)
    job.setPriority(priority)
    job.schedule()
    job
  }

  /** Create and schedule a job with the given name. Default values for scheduling rules and priority are taken
   *  from the `Job` implementation.
   *
   *  @param rule The scheduling rule
   *  @param priority The job priority (defaults to Job.LONG, like the platform `Job` class)
   *  @return The job
   */
  def scheduleJob(name: String, rule: ISchedulingRule = null, priority: Int = Job.LONG)(f: IProgressMonitor => IStatus): Job = {
    val j = prepareJob(name, rule, priority)(f)
    j.schedule()
    j
  }

  def computeSelectedResources(selection: IStructuredSelection): List[IResource] =
    IDE.computeSelectedResources(selection).toList.asInstanceOf[List[IResource]]

  object SelectedItems {
    def unapplySeq(selection: ISelection): Option[List[Any]] = condOpt(selection) {
      case structuredSelection: IStructuredSelection => structuredSelection.toArray.toList
    }
  }

  def getWorkbenchPages: List[IWorkbenchPage] =
    for {
      window <- PlatformUI.getWorkbench.getWorkbenchWindows.toList
      page <- window.getPages
    } yield page

}