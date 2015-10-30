package org.scalaide.util.eclipse

import scala.collection.JavaConversions._
import org.eclipse.core.resources._
import org.eclipse.core.runtime._
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.text.edits.ReplaceEdit
import org.eclipse.text.edits.{ TextEdit => EclipseTextEdit }
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
import scalariform.utils.TextEdit
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.swt.graphics.RGB
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.PlatformUI
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.runtime.jobs.ISchedulingRule
import org.scalaide.logging.HasLogger
import org.eclipse.ui.IWorkbenchWindow
import java.io.FileNotFoundException
import scala.util.Try

object EclipseUtils extends HasLogger {

  implicit class RichAdaptable(adaptable: IAdaptable) {

    /** Returns an object which is an instance of the given class
     *  associated with this object. Returns `null` if
     *  no such object can be found.
     *
     *  @see [[org.eclipse.core.runtime.IAdaptable.getAdapter]]
     */
    def adaptTo[T](implicit m: Manifest[T]): T = adaptable.getAdapter(m.runtimeClass).asInstanceOf[T]

    /** Returns an object which is an instance of the given class
     *  associated with this object. Returns None if
     *  no such object can be found.
     *
     *  @see [[adaptTo]]
     */
    def adaptToOpt[T](implicit m: Manifest[T]): Option[T] = Option(adaptable.getAdapter(m.runtimeClass).asInstanceOf[T])

  }

  implicit class RichPreferenceStore(preferenceStore: IPreferenceStore) {

    /** Returns the current value of the color-valued preference with the
     *  given name in the given preference store.
     *  @see [[org.eclipse.jface.preference.PreferenceConverter]]
     */
    def getColor(key: String): RGB = PreferenceConverter.getColor(preferenceStore, key)

  }

  implicit class RichDocument(document: IDocument) {

    /** Returns the character at the given document offset in this document.
     *
     *  @see [[org.eclipse.jface.text.IDocument.getChar]]
     */
    def apply(offset: Int): Char = document.getChar(offset)

  }

  private[scalaide] implicit class RichWorkbench(w: IWorkbenchWindow) {
    def serviceOf[A: reflect.ClassTag]: A =
      w.getService(reflect.classTag[A].runtimeClass).asInstanceOf[A]
  }

  /** A sequence extractor which takes an [[ISelection]] and returns the [[IStructuredSelection]] that it may contain.
   *  @see [[IStructuredSelection]]
   */
  object SelectedItems {

    def unapplySeq(selection: ISelection): Option[List[Any]] = PartialFunction.condOpt(selection) {
      case structuredSelection: IStructuredSelection => structuredSelection.toArray.toList
    }

  }

  private[scalaide] def asEclipseTextEdit(edit: TextEdit): EclipseTextEdit =
    new ReplaceEdit(edit.position, edit.length, edit.replacement)

  /** Run the given function as a workspace runnable inside `wspace`.
   *
   *  @param wspace the workspace
   *  @param monitor the progress monitor (defaults to `null` for no progress monitor).
   */
  def workspaceRunnableIn(wspace: IWorkspace, monitor: IProgressMonitor = null)(f: IProgressMonitor => Unit): Unit = {
    wspace.run(new IWorkspaceRunnable {
      override def run(monitor: IProgressMonitor): Unit = {
        f(monitor)
      }
    }, monitor)
  }

  /** Create a job with the given name. Default values for scheduling rules and priority are taken
   *  from the `Job` implementation.
   *
   *  @param rule The scheduling rule
   *  @param priority The job priority (defaults to Job.LONG, like the platform `Job` class)
   *  @return The job
   */
  private[scalaide] def prepareJob(name: String, rule: ISchedulingRule = null, priority: Int = Job.LONG)(f: IProgressMonitor => IStatus): Job = {
    val job = new Job(name) {
      override def run(monitor: IProgressMonitor): IStatus = f(monitor)
    }
    job.setRule(rule)
    job.setPriority(priority)
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

  /** Extracts and returns the IResources in the given
   *  selection or the resource objects they adapts to.
   *
   *  @see `org.eclipse.ui.ide.IDE.computeSelectedResources`
   */
  def computeSelectedResources(selection: IStructuredSelection): List[IResource] =
    IDE.computeSelectedResources(selection).toList.asInstanceOf[List[IResource]]

  /** Returns a list of pages in all the open main windows associated with this workbench.
   *
   */
  def getWorkbenchPages: List[IWorkbenchPage] =
    for {
      wbench <- Try(List(PlatformUI.getWorkbench)).getOrElse(Nil)
      window <- wbench.getWorkbenchWindows.toList
      page <- window.getPages
    } yield page

  /** Returns the location of the source bundle for the bundle.
   *
   *  @param bundleId the bundle id
   *  @param bundelPath the bundle location
   */
  def computeSourcePath(bundleId: String, bundlePath: IPath): Option[IPath] = {
    val jarFile = bundlePath.lastSegment()
    val parentFolder = bundlePath.removeLastSegments(1)

    val sourceBundleId = bundleId + ".source"
    // the expected filename for the source jar
    val sourceJarFile = jarFile.replace(bundleId, sourceBundleId)

    // the source jar location when the files are from the plugins folder
    val installedLocation = parentFolder / sourceJarFile

    if (installedLocation.toFile().exists()) {
      // found in the plugins folder
      Some(installedLocation)
    } else {
      val versionString = parentFolder.lastSegment()
      val groupFolder = parentFolder.removeLastSegments(2)
      // the source jar location when the files are from a local m2 repo
      val buildLocation = groupFolder / sourceBundleId / versionString / sourceJarFile
      if (buildLocation.toFile().exists()) {
        // found in the m2 repo
        Some(buildLocation)
      } else {
        // not found
        None
      }
    }
  }

  implicit class RichPath(val p: IPath) extends AnyVal {
    def /(segment: String): IPath =
      p append segment

    def /(other: IPath): IPath =
      p append other
  }

  /** Returns all existing configuration elements of a given extension point ID.
   *  Returns an empty array if the ID is not found.
   */
  def configElementsForExtension(id: String): Array[IConfigurationElement] =
    Platform.getExtensionRegistry().getConfigurationElementsFor(id)

  /**
   * Executes a given function `f` in a safe runner that catches potential
   * occurring exceptions and logs them together with `errorMsg` if this is the
   * case.
   *
   * If no error occurs, the result of `f` is returned, otherwise `None`.
   */
  def withSafeRunner[A](errorMsg: => String)(f: => A): Option[A] = {
    var res: Option[A] = None
    SafeRunner.run(new ISafeRunnable {
      override def handleException(e: Throwable) =
        eclipseLog.error(s"$errorMsg. Check the previous stack trace for more information.")

      override def run() = { res = Option(f) }
    })
    res
  }

  /** Returns the root resource of this workspace.
   *
   *  @see `org.eclipse.core.resources.IWorkspace.getRoot`
   */
  def workspaceRoot: IWorkspaceRoot = ResourcesPlugin.getWorkspace.getRoot


  def projectFromPath(project: IPath): IProject = EclipseUtils.workspaceRoot.getProject(project.toString)
}
