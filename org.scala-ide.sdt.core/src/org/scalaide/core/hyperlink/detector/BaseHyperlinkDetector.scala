package org.scalaide.core.hyperlink.detector

import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.ui.texteditor.ITextEditor
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.util.internal.eclipse.EditorUtils
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration._
import java.util.concurrent.TimeoutException
import org.scalaide.core.ScalaPlugin
import org.scalaide.logging.HasLogger

abstract class BaseHyperlinkDetector extends AbstractHyperlinkDetector with HasLogger {
  val TIMEOUT = if (ScalaPlugin.plugin.noTimeoutMode) Duration.Inf else 500.millis

  final override def detectHyperlinks(viewer: ITextViewer, currentSelection: IRegion, canShowMultipleHyperlinks: Boolean): Array[IHyperlink] = {
    val textEditor = getAdapter(classOf[ITextEditor]).asInstanceOf[ITextEditor]
    detectHyperlinks(textEditor, currentSelection, canShowMultipleHyperlinks)
  }

  /** The Eclipse platform calls this method on the UI thread, so we do our best, but return null
   *  after TIMEOUT milliseconds. 500 ms is enough to locate most hyperlinks, but it may timeout for
   *  the first request on a fresh project.
   *
   *  That seems a better experience than freezing the editor for an undetermined amount of time.
   */
  final def detectHyperlinks(textEditor: ITextEditor, currentSelection: IRegion, canShowMultipleHyperlinks: Boolean): Array[IHyperlink] = {
    if (textEditor == null) null // can be null if generated through ScalaPreviewerFactory
    else {
      EditorUtils.getEditorCompilationUnit(textEditor) match {
        case Some(scu) =>
          val hyperlinks = Future { runDetectionStrategy(scu, textEditor, currentSelection) }

          try {
            Await.result(hyperlinks, TIMEOUT) match {
              // I know you will be tempted to remove this, but don't do it, JDT expects null when no hyperlinks are found.
              case Nil => null
              case links =>
                if (canShowMultipleHyperlinks) links.toArray
                else Array(links.head)
            }
          } catch {
            case e: TimeoutException =>
              eclipseLog.info("Timeout while resolving hyperlink in " + scu.file + " at: " + currentSelection)
              null
          }

        case None => null
      }
    }
  }

  // FIXME: Why is this needed? Why not just increase visibility of `runDetectionStrategy` itself?
  private[detector] def friendRunDetectionStrategy(scu: InteractiveCompilationUnit, textEditor: ITextEditor, currentSelection: IRegion): List[IHyperlink] =
    runDetectionStrategy(scu, textEditor, currentSelection)

  protected def runDetectionStrategy(scu: InteractiveCompilationUnit, textEditor: ITextEditor, currentSelection: IRegion): List[IHyperlink]
}
