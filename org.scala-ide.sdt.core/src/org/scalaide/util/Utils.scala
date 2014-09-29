package org.scalaide.util

import scala.reflect.ClassTag
import scala.reflect.io.AbstractFile
import scala.tools.eclipse.contribution.weaving.jdt.IScalaWordFinder
import scala.util.Try

import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.IBuffer
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.viewers.ISelection
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.swt.graphics.RGB
import org.eclipse.ui.texteditor.ITextEditor
import org.osgi.framework.Bundle
import org.eclipse.jface.viewers.DoubleClickEvent
import org.eclipse.jface.viewers.IDoubleClickListener
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.swt.events.SelectionEvent
import scala.util.Try
import org.eclipse.core.resources.IResource
import java.io.File
import org.eclipse.core.runtime.Path
import scala.reflect.io.AbstractFile
import org.eclipse.core.resources.IMarker
import org.eclipse.jface.resource.ImageDescriptor
import java.io.IOException
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.ui.IEditorPart
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.Position
import org.eclipse.swt.events.KeyEvent
import org.eclipse.swt.widgets.Control
import org.eclipse.pde.internal.ui.util.SWTUtil
import org.eclipse.swt.events.MouseAdapter
import org.eclipse.swt.widgets.Shell
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.core.resources.IWorkspaceRoot
import org.eclipse.core.runtime.jobs.ISchedulingRule
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.core.runtime.IConfigurationElement
import org.eclipse.core.resources.IWorkspace
import org.scalaide.ui.internal.editor.ISourceViewerEditor
import org.scalaide.core.compiler.InteractiveCompilationUnit

trait UtilsImplicits {

  abstract class WithAsInstanceOfOpt(obj: AnyRef) {
    /** Half type-safe cast. It uses erasure semantics (like Java casts). For example:
     *
     *  xs: List[Int]
     *
     *  xs.asInstanceOfOpt[List[Int]] == xs.asInstanceOfOpt[List[Double]] == xs.asInstanceOfOpt[Seq[Int]] == Some(xs)
     *
     *  and
     *
     *  xs.asInstanceOfOpt[String] == xs.asInstanceOfOpt[Set[Int]] == None
     *
     *  @return None if the cast fails or the object is null, Some[B] otherwise
     */
    def asInstanceOfOpt[B: ClassTag]: Option[B]
  }

  abstract class PimpedPreferenceStore(preferenceStore: IPreferenceStore) {
    def getColor(key: String): RGB = PreferenceConverter.getColor(preferenceStore, key)
  }

  abstract class PimpedDocument(document: IDocument) {
    def apply(offset: Int): Char
  }

  abstract class PimpedAdaptable(adaptable: IAdaptable) {

    def adaptTo[T](implicit m: Manifest[T]): T

    def adaptToSafe[T](implicit m: Manifest[T]): Option[T]

  }

  def withAsInstanceOfOpt(obj:AnyRef): WithAsInstanceOfOpt
  def pimpedPreferenceStore(preferenceStore: IPreferenceStore):PimpedPreferenceStore
  def pimpedDocument(document: IDocument): PimpedDocument
  def pimpedAdaptable(adaptable: IAdaptable): PimpedAdaptable
}

object UtilsImplicits extends UtilsImplicits {
  import org.scalaide.util.internal.Utils.{WithAsInstanceOfOpt => WithAsInstanceOfOptImplem}
  import org.scalaide.util.internal.eclipse.EclipseUtils.{PimpedPreferenceStore => PimpedPreferenceStoreImplem}
  import org.scalaide.util.internal.eclipse.EclipseUtils.{PimpedDocument => PimpedDocumentImplem}
  import org.scalaide.util.internal.eclipse.EclipseUtils.{PimpedAdaptable => PimpedAdaptableImplem}

  implicit def withAsInstanceOfOpt(obj: AnyRef) = WithAsInstanceOfOptImplem(obj)
  implicit def pimpedPreferenceStore(preferenceStore: IPreferenceStore) = PimpedPreferenceStoreImplem(preferenceStore)
  implicit def pimpedDocument(document: IDocument) = PimpedDocumentImplem(document)
  implicit def pimpedAdaptable(adaptable: IAdaptable) = PimpedAdaptableImplem(adaptable)

}

abstract class PimpedControl(control: Control) {

  def onKeyReleased(p: KeyEvent => Any): Unit

  def onKeyReleased(p: => Any): Unit

  def onFocusLost(p: => Any): Unit

}

trait EclipseUtils {

  /** Run the given function as a workspace runnable inside `wspace`.
   *
   *  @param wspace the workspace
   *  @param monitor the progress monitor (defaults to null for no progress monitor).
   */
  def workspaceRunnableIn(wspace: IWorkspace, monitor: IProgressMonitor = null)(f: IProgressMonitor => Unit)

  /**
   * Returns all existing configuration elements of a given extension point ID.
   * Returns an empty array if the ID is not found.
   */
  def configElementsForExtension(id: String): Array[IConfigurationElement]

  /**
   * Executes a given function in a safe runner that catches potential occuring
   * exceptions and logs them if this is the case.
   */
  def withSafeRunner(f: => Unit): Unit

  /** Create and schedule a job with the given name. Default values for scheduling rules and priority are taken
   *  from the `Job` implementation.
   *
   *  @param rule The scheduling rule
   *  @param priority The job priority (defaults to Job.LONG, like the platform `Job` class)
   *  @return The job
   */
  def scheduleJob(name: String, rule: ISchedulingRule = null, priority: Int = Job.LONG)(f: IProgressMonitor => IStatus): Job

  /**
   * Returns the location of the source bundle for the bundle.
   *
   * @param bundleId the bundle id
   * @param bundelPath the bundle location
   */
  def computeSourcePath(bundleId: String, bundlePath: IPath): Option[IPath]

  def getWorkbenchPages: List[IWorkbenchPage]
  def computeSelectedResources(selection: IStructuredSelection): List[IResource]
  def workspaceRoot: IWorkspaceRoot
}

object EclipseUtils {
  def apply(): EclipseUtils = org.scalaide.util.internal.eclipse.EclipseUtils
}

trait SWTUtils {

  def pimpedControl(control: Control): PimpedControl
  def fnToSelectionAdapter(p: SelectionEvent => Any): SelectionAdapter
  def fnToDoubleClickListener(p: DoubleClickEvent => Any): IDoubleClickListener
  def fnToPropertyChangeListener(p: PropertyChangeEvent => Any): IPropertyChangeListener
  def fnToSelectionChangedEvent(p:SelectionChangedEvent => Unit): ISelectionChangedListener
  def noArgFnToSelectionChangedListener(p: () => Any): ISelectionChangedListener
  def noArgFnToSelectionAdapter(p: () => Any): SelectionAdapter
  def noArgFnToMouseUpListener(f: () => Any): MouseAdapter
  def fnToModifyListener(f: ModifyEvent => Unit): ModifyListener
  def getShell: Shell
  def getWorkbenchWindow: Option[IWorkbenchWindow]

}

object SWTUtils extends SWTUtils {

  import org.scalaide.util.internal.eclipse.{SWTUtils => SWTUtilsImplem}

  implicit def pimpedControl(control:Control): PimpedControl = new SWTUtilsImplem.PimpedControl(control)
  implicit def fnToSelectionAdapter(p: SelectionEvent => Any) = SWTUtilsImplem.fnToSelectionAdapter(p)
  implicit def fnToDoubleClickListener(p: DoubleClickEvent => Any) = SWTUtilsImplem.fnToDoubleClickListener(p)
  implicit def fnToPropertyChangeListener(p: PropertyChangeEvent => Any) = SWTUtilsImplem.fnToPropertyChangeListener(p)
  implicit def fnToSelectionChangedEvent(p:SelectionChangedEvent => Unit) = SWTUtilsImplem.fnToSelectionChangedEvent(p)
  implicit def noArgFnToSelectionChangedListener(p: () => Any) = SWTUtilsImplem.noArgFnToSelectionChangedListener(p)
  implicit def noArgFnToSelectionAdapter(p: () => Any) = SWTUtilsImplem.noArgFnToSelectionAdapter(p)
  implicit def noArgFnToMouseUpListener(f: () => Any): MouseAdapter = SWTUtilsImplem.noArgFnToMouseUpListener(f)
  implicit def fnToModifyListener(f: ModifyEvent => Unit): ModifyListener = SWTUtils.fnToModifyListener(f)
  def getShell: Shell= SWTUtilsImplem.getShell
  def getWorkbenchWindow: Option[IWorkbenchWindow] = SWTUtilsImplem.getWorkbenchWindow

}

trait DisplayThread {

  /** Asynchronously run `f` on the UI thread.  */
  def asyncExec(f: => Unit): Unit

  /** Synchronously run `f` on the UI thread.  */
  def syncExec(f: => Unit): Unit

}

object DisplayThread {
  def apply(): DisplayThread = org.scalaide.util.internal.ui.DisplayThread
}

object SelectedItems  {
  def unapplySeq(selection: ISelection): Option[List[Any]] = PartialFunction.condOpt(selection) {
    case structuredSelection: IStructuredSelection => structuredSelection.toArray.toList
  }
}

trait OSGiUtils {
  def pathInBundle(bundle: Bundle, path: String): Option[IPath]

  /** accepts `null`*/
  def getBundlePath(bundle: Bundle): Option[IPath]

  /**
   * Read the content of a file whose `filePath` points to a location in a
   * given `bundleId` and returns them. A [[scala.util.Failure]] is returned if
   * either the file could not be found or if it could not be accessed.
   */
  def fileContentFromBundle(bundleId: String, filePath: String): util.Try[String]

    /** Returns the ImageDescriptor for the image at the given {{path}} in
   *  the Scala IDE core bundle.
   *  Returns the default missing image descriptor if the path is invalid.
   */
  def getImageDescriptorFromCoreBundle(path: String): ImageDescriptor

    /** Returns the ImageDescriptor for the image at the given {{path}} in
   *  the bundle with the given {{id}}.
   *  Returns the default missing image descriptor if the bundle is not
   *  in a running state, or if the path is invalid.
   */
  def getImageDescriptorFromBundle(bundleId: String, path: String): ImageDescriptor
}

object OSGiUtils {
  def apply(): OSGiUtils = org.scalaide.util.internal.eclipse.OSGiUtils
}

trait FileUtils {

  def clearBuildErrors(file: IFile, monitor: IProgressMonitor): Unit

  def hasBuildErrors(file: IResource): Boolean

  /** Creates a file of a given `IFile` and all of its parent folders if needed.
   *  Resource listeners are also notified about the changes.
   *
   *  Returns `Unit` if the file creation was successful, otherwise the thrown
   *  exception.
   */
  def createFile(file: IFile): Try[Unit]

  /** Delete directory recursively. Does nothing if dir is not a directory. */
  def deleteDir(dir: File): Unit

  def clearTasks(file: IFile, monitor: IProgressMonitor): Unit

  def toIFile(file: AbstractFile): Option[IFile]

  /** Is the file buildable by the Scala plugin? In other words, is it a
   *  Java or Scala source file?
   *
   *  @note If you don't have an IFile yet, prefer the String overload, as
   *        creating an IFile is usually expensive
   */
  def isBuildable(file: IFile): Boolean
  def isBuildable(fileName: String): Boolean

  /**
   * Find a File that matches the given absolute location on the file systme. Since a given
   * file might "mounted" under multiple locations in the Eclipse file system, the `prefix`
   * path is used disambiguate.
   */
  def resourceForPath(location: IPath, prefix: IPath = Path.EMPTY): Option[IFile]

  def findBuildErrors(file: IResource): Seq[IMarker]
}

object FileUtils {
  def apply(): FileUtils = org.scalaide.util.internal.eclipse.FileUtils
}

trait ScalaWordFinder extends IScalaWordFinder {

  def findWord(document: IDocument, offset: Int): IRegion

  def findWord(buffer: IBuffer, offset: Int): IRegion

  def findWord(document: IndexedSeq[Char], offset: Int): IRegion

  def findCompletionPoint(document: IDocument, offset: Int): IRegion

  def findCompletionPoint(buffer: IBuffer, offset: Int): IRegion

  def findCompletionPoint(document: IndexedSeq[Char], offset0: Int): IRegion

  /** Returns the length of the identifier which is located at the offset position. */
  def identLenAtOffset(doc: IDocument, offset: Int): Int
}

object ScalaWordFinder {
  def apply(): ScalaWordFinder = org.scalaide.util.internal.ScalaWordFinder
}

trait EditorUtils {

  def findOrOpen(file: IFile): Option[IDocument]

  def textEditor(e: IEditorPart): Option[ISourceViewerEditor]

  def withCurrentEditor[T](block: ISourceViewerEditor => Option[T]): Option[T]

  def doWithCurrentEditor(block: ISourceViewerEditor => Unit): Unit

  def enterLinkedModeUi(ps: List[(Int, Int)], selectFirst: Boolean): Unit

  def getEditorCompilationUnit(editor: ITextEditor): Option[InteractiveCompilationUnit]

  def enterMultiLinkedModeUi(positionGroups: List[List[(Int, Int)]], selectFirst: Boolean): Unit

  def getAnnotationsAtOffset(part: org.eclipse.ui.IEditorPart, offset: Int): Iterator[(Annotation, Position)]

  def getTextSelection(editor: ITextEditor): Option[ITextSelection]

  def openEditorAndApply[T](element: IJavaElement)(editor: IEditorPart => T): T

  /**
   * Returns the resource of the active editor if it exists.
   *
   * This method returns `None` in the following cases:
   * - It is not executed on the UI thread
   * - The active selection is not an editor
   * - The active editor doesn't provide a resource (which is the case if an
   *   [[IClassFile]] is opened)
   */
  def resourceOfActiveEditor: Option[IResource]

  def textSelection2region(selection: ITextSelection): IRegion

  def withScalaFileAndSelection[T](block: (InteractiveCompilationUnit, ITextSelection) => Option[T]): Option[T]

  def withCurrentScalaSourceFile[T](block: ScalaSourceFile => T): Option[T]

  def withScalaSourceFileAndSelection[T](block: (ScalaSourceFile, ITextSelection) => Option[T]): Option[T]

  /**
   * Given an `ISourceViewer` it applies `f` on the underlying document's model.
   * If one of the involved components is `null`, even if `f` returns `null`, this
   * method returns `None`, otherwise the result of `f`.
   *
   * This method is UI independent.
   */
  def withDocument[A](sourceViewer: ISourceViewer)(f: IDocument => A): Option[A]

}

object EditorUtils {
  def apply(): EditorUtils = org.scalaide.util.internal.eclipse.EditorUtils
}

trait Utils {

   /** Try executing the passed `action` and log any exception occurring. */
  def tryExecute[T](action: => T, msgIfError: => Option[String] = None): Option[T]

  /** Evaluated `op' and log the time in ms it took to execute it.
   */
  def debugTimed[A](name: String)(op: => A): A

}

object Utils {
  def apply(): Utils = org.scalaide.util.internal.Utils
}