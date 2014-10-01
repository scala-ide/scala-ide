package org.scalaide.util.internal.eclipse

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

object EclipseUtils extends HasLogger with org.scalaide.util.EclipseUtils {

  implicit class RichAdaptable(adaptable: IAdaptable) extends org.scalaide.util.UtilsImplicits.RichAdaptable(adaptable) {

    def adaptTo[T](implicit m: Manifest[T]): T = adaptable.getAdapter(m.runtimeClass).asInstanceOf[T]

    def adaptToOpt[T](implicit m: Manifest[T]): Option[T] = Option(adaptable.getAdapter(m.runtimeClass).asInstanceOf[T])

  }

  implicit class RichPreferenceStore(preferenceStore: IPreferenceStore) extends org.scalaide.util.UtilsImplicits.RichPreferenceStore(preferenceStore) {

    override def getColor(key: String): RGB = PreferenceConverter.getColor(preferenceStore, key)

  }

  implicit class RichDocument(document: IDocument) extends org.scalaide.util.UtilsImplicits.RichDocument(document) {

    def apply(offset: Int): Char = document.getChar(offset)

  }

  implicit class RichWorkbench(w: IWorkbenchWindow) {
    def serviceOf[A : reflect.ClassTag]: A =
      w.getService(reflect.classTag[A].runtimeClass).asInstanceOf[A]
  }

  def asEclipseTextEdit(edit: TextEdit): EclipseTextEdit =
    new ReplaceEdit(edit.position, edit.length, edit.replacement)

  def workspaceRunnableIn(wspace: IWorkspace, monitor: IProgressMonitor = null)(f: IProgressMonitor => Unit) = {
    wspace.run(new IWorkspaceRunnable {
      def run(monitor: IProgressMonitor) {
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
  def prepareJob(name: String, rule: ISchedulingRule = null, priority: Int = Job.LONG)(f: IProgressMonitor => IStatus): Job = {
    val job = new Job(name) {
      override def run(monitor: IProgressMonitor): IStatus = f(monitor)
    }
    job.setRule(rule)
    job.setPriority(priority)
    job
  }

  def scheduleJob(name: String, rule: ISchedulingRule = null, priority: Int = Job.LONG)(f: IProgressMonitor => IStatus): Job = {
    val j = prepareJob(name, rule, priority)(f)
    j.schedule()
    j
  }

  def computeSelectedResources(selection: IStructuredSelection): List[IResource] =
    IDE.computeSelectedResources(selection).toList.asInstanceOf[List[IResource]]

  def getWorkbenchPages: List[IWorkbenchPage] =
    for {
      wbench <- Try(List(PlatformUI.getWorkbench)).getOrElse(Nil)
      window <- wbench.getWorkbenchWindows.toList
      page <- window.getPages
    } yield page

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

  def configElementsForExtension(id: String): Array[IConfigurationElement] =
    Platform.getExtensionRegistry().getConfigurationElementsFor(id)

  def withSafeRunner(f: => Unit): Unit = {
    SafeRunner.run(new ISafeRunnable {
      override def handleException(e: Throwable) =
        eclipseLog.error("Error occured while executing extension.", e)

      override def run() = f
    })
  }

  def workspaceRoot = ResourcesPlugin.getWorkspace.getRoot

}
