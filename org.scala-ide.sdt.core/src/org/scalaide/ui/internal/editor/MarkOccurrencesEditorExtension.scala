package org.scalaide.ui.internal.editor

import org.eclipse.core.runtime.Status
import org.eclipse.core.runtime.jobs.Job
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jface.text.IDocumentExtension4
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.IAnnotationModel
import org.eclipse.jface.viewers.ISelection
import org.eclipse.ui.ISelectionListener
import org.eclipse.ui.IWorkbenchPart
import org.scalaide.core.internal.decorators.markoccurrences.Occurrences
import org.scalaide.core.internal.decorators.markoccurrences.ScalaOccurrencesFinder
import org.scalaide.util.Utils
import org.scalaide.util.eclipse.EclipseUtils
import org.scalaide.util.eclipse.EditorUtils
import org.scalaide.util.internal.eclipse.AnnotationUtils._

/**
 * Contains functionality to enable the mark occurrences feature for Scala
 * compilation unit editors.
 */
trait MarkOccurrencesEditorExtension extends ScalaCompilationUnitEditor {

  // needs to be lazy because [[getInteractiveCompilationUnit]] succeeds only after the editor is fully loaded
  private lazy val occurrencesFinder = new ScalaOccurrencesFinder(getInteractiveCompilationUnit)

  private var occurrenceAnnotations: Set[Annotation] = Set()
  private var occurencesFinderInstalled = false
  private var runningJob: Job = _

  private lazy val selectionListener = new ISelectionListener() {
    override def selectionChanged(part: IWorkbenchPart, selection: ISelection) = {
      selection match {
        case textSel: ITextSelection => requireOccurrencesUpdate(textSel)
        case _ =>
      }
    }
  }

  override def updateOccurrenceAnnotations(selection: ITextSelection, astRoot: CompilationUnit): Unit = {
    requireOccurrencesUpdate(selection)
  }

  override def installOccurrencesFinder(forceUpdate: Boolean): Unit = {
    if (!occurencesFinderInstalled) {
      getEditorSite.getPage.addPostSelectionListener(selectionListener)
      occurencesFinderInstalled = true
    }
  }

  override def uninstallOccurrencesFinder(): Unit = {
    occurencesFinderInstalled = false
    getEditorSite.getPage.removePostSelectionListener(selectionListener)
    removeScalaOccurrenceAnnotations()
  }

  /** Clear the existing Mark Occurrences annotations.
   */
  private def removeScalaOccurrenceAnnotations() = {
    for (annotationModel <- getAnnotationModelOpt) annotationModel.withLock {
      annotationModel.replaceAnnotations(occurrenceAnnotations, Map())
      occurrenceAnnotations = Set()
    }
  }

  private def requireOccurrencesUpdate(selection: ITextSelection): Unit = {
    def spawnNewJob(lastModified: Long) = {
      runningJob = EclipseUtils.scheduleJob("Updating occurrence annotations", priority = Job.DECORATE) { monitor =>
        Option(getInteractiveCompilationUnit) foreach { cu =>
          val fileName = cu.file.name
          Utils.debugTimed(s"""Time elapsed for "updateOccurrences" in source $fileName""") {
            performOccurrencesUpdate(selection, lastModified)
          }
        }
        Status.OK_STATUS
      }
    }

    if (selection.getLength >= 0
        && selection.getOffset >= 0
        && getDocumentProvider != null
        && EditorUtils.isActiveEditor(this))
      sourceViewer.getDocument match {
        // don't spawn a new job when another one is already running
        case document: IDocumentExtension4 if runningJob == null || runningJob.getState == Job.NONE ⇒
          spawnNewJob(document.getModificationStamp)
        case _ =>
      }
  }

  private def performOccurrencesUpdate(selection: ITextSelection, documentLastModified: Long) = {
    val annotations = getAnnotations(selection, documentLastModified)
    for(annotationModel <- getAnnotationModelOpt) annotationModel.withLock {
      annotationModel.replaceAnnotations(occurrenceAnnotations, annotations)
      occurrenceAnnotations = annotations.keySet
    }
  }

  private def getAnnotations(selection: ITextSelection, documentLastModified: Long): Map[Annotation, Position] = {
    val region = EditorUtils.textSelection2region(selection)
    val occurrences = occurrencesFinder.findOccurrences(region, documentLastModified)
    for {
      Occurrences(name, locations) <- occurrences.toList
      location <- locations
      annotation = new Annotation(ScalaSourceFileEditor.OCCURRENCE_ANNOTATION, false, "Occurrence of '" + name + "'")
      position = new Position(location.getOffset, location.getLength)
    } yield annotation -> position
  }.toMap

  /** Returns the annotation model of the current document provider.
   */
  private def getAnnotationModelOpt: Option[IAnnotationModel] = {
    for {
      documentProvider <- Option(getDocumentProvider)
      annotationModel <- Option(documentProvider.getAnnotationModel(getEditorInput))
    } yield annotationModel
  }
}
