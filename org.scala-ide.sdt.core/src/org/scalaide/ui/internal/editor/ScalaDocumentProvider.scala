package org.scalaide.ui.internal.editor

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IResourceStatus
import org.eclipse.core.runtime.Assert
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.SubProgressMonitor
import org.eclipse.jdt.core.IJavaModelStatusConstants
import org.eclipse.jdt.core.JavaModelException
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider.CompilationUnitInfo
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.text.IDocument
import org.eclipse.ui.texteditor.AbstractMarkerAnnotationModel
import org.scalaide.logging.HasLogger

class ScalaDocumentProvider
    extends CompilationUnitDocumentProvider
    with HasLogger
    with SaveActionExtensions {

  /** Indicates whether the save has been initialized by this provider. */
  private var saveInitialized = false

  override def saveDocumentContent(m: IProgressMonitor, element: AnyRef, doc: IDocument, overwrite: Boolean): Unit = {
    if (!saveInitialized)
      super.saveDocumentContent(m, element, doc, overwrite)
  }

  override def commitWorkingCopy(m: IProgressMonitor, element: AnyRef, info: CompilationUnitInfo, overwrite: Boolean): Unit = {
    val monitor = if (m == null) new NullProgressMonitor() else m
    monitor.beginTask("", 100)

    try commitWorkingCopy0(monitor, element, info, overwrite)
    catch {
      case e: Exception =>
        logger.error("exception thrown while trying to save document", e)
    }
    finally {
      monitor.done()
    }
  }

  /**
   * The implementation of this method is copied/adapted from the class
   * [[org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider#commitWorkingCopy]].
   *
   * `super.createSaveOperation` is the method that should be overwritten, but
   * this method does too many useful things. I'm afraid when reimplementing
   * it I break too many things.
   */
  private def commitWorkingCopy0(monitor: IProgressMonitor, element: AnyRef, info: CompilationUnitInfo, overwrite: Boolean): Unit = {
    val document = info.fTextFileBuffer.getDocument()
    val resource = {
      val r = info.fCopy.getResource()
      Assert.isTrue(r.isInstanceOf[IFile])
      r.asInstanceOf[IFile]
    }

    val isSynchronized = resource.isSynchronized(IResource.DEPTH_ZERO)

    /* https://bugs.eclipse.org/bugs/show_bug.cgi?id=98327
     * Make sure file gets save in commit() if the underlying file has been deleted */
    if (!isSynchronized && isDeleted(element))
      info.fTextFileBuffer.setDirty(true)

    if (!resource.exists()) {
      // underlying resource has been deleted, just recreate file, ignore the rest
      createFileFromDocument(monitor, resource, document)
      return
    }

    var subMonitor: IProgressMonitor = null
    try {
      saveInitialized = true

      // the Java editor calls [[CleanUpPostSaveListener]] here and calculates
      // changed regions which we don't need
      val doc = info.fTextFileBuffer.getDocument()
      val listeners = Array(createScalaSaveActionListener(doc))

      subMonitor = getSubProgressMonitor(monitor, if (listeners.length > 0) 70 else 100)

      info.fCopy.commitWorkingCopy(overwrite || isSynchronized, subMonitor)
      if (listeners.length > 0)
        notifyPostSaveListeners(info, Array(), listeners, getSubProgressMonitor(monitor, 30))

    } catch {
      // inform about the failure
      case x: JavaModelException =>
        fireElementStateChangeFailed(element)
        if (IJavaModelStatusConstants.UPDATE_CONFLICT == x.getStatus().getCode())
          // convert JavaModelException to CoreException
          throw new CoreException(new Status(
              IStatus.WARNING, JavaUI.ID_PLUGIN, IResourceStatus.OUT_OF_SYNC_LOCAL,
              "The file is not synchronized with the local file system.", null))
        throw x
      case x: CoreException =>
        // inform about the failure
        fireElementStateChangeFailed(element)
        throw x
      case x: RuntimeException =>
        // inform about the failure
        fireElementStateChangeFailed(element)
        throw x
    } finally {
      saveInitialized = false
      if (subMonitor != null)
        subMonitor.done()
    }

    // If here, the dirty state of the editor will change to "not dirty".
    // Thus, the state changing flag will be reset.
    if (info.fModel.isInstanceOf[AbstractMarkerAnnotationModel]) {
      val model = info.fModel.asInstanceOf[AbstractMarkerAnnotationModel]
      model.updateMarkers(document)
    }
  }

  private def getSubProgressMonitor(monitor: IProgressMonitor, ticks: Int): IProgressMonitor =
    if (monitor != null)
      new SubProgressMonitor(monitor, ticks, SubProgressMonitor.PREPEND_MAIN_LABEL_TO_SUBTASK)
    else
      new NullProgressMonitor()

}
