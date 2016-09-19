package org.scalaide.ui.internal.editor.spelling

import org.eclipse.core.runtime.content.IContentType
import org.eclipse.jdt.core.IProblemRequestor
import org.eclipse.jdt.internal.ui.text.spelling.CoreSpellingProblem
import org.eclipse.jdt.internal.ui.text.spelling.JavaSpellingProblem
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.reconciler.DirtyRegion
import org.eclipse.jface.text.source.IAnnotationModel
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.ui.texteditor.spelling.ISpellingProblemCollector
import org.eclipse.ui.texteditor.spelling.SpellingProblem
import org.eclipse.ui.texteditor.spelling.{ SpellingReconcileStrategy => ESpellingReconcileStrategy }
import org.eclipse.ui.texteditor.spelling.{ SpellingService => ESpellingService }

/**
 * Reconcile strategy for spell checking. It checks whether spell checking is
 * enabled and delegates spell errors as problems to a [[IProblemRequestor]].
 *
 * The implementation of this class is adopted from
 * [[JavaSpellingReconcileStrategy]], which couldn't be used because it sets the
 * spelling service in its constructor. The spelling service is the only thing
 * we had to modify to enable a Scala spelling engine, for a more detailed
 * description see [[ScalaSpellingService]].
 *
 * @param editor
 *        the editor on whose document the spelling engine should operate on
 * @param viewer
 *        the source viewer of the editor
 * @param spellingService
 *        the service that forwards spelling check requests to a spelling engine
 * @param contentType
 *        the content type of the source file the editor in operating on
 * @param store
 *        the preference store that stores the property whether spell checking
 *        is enabled
 */
final class SpellingReconcileStrategy(
      editor: ITextEditor,
      viewer: ISourceViewer,
      spellingService: SpellingService,
      contentType: IContentType,
      store: IPreferenceStore)
    extends ESpellingReconcileStrategy(viewer, spellingService) {

  private var requestor: Option[IProblemRequestor] = None

  override def setDocument(document: IDocument): Unit = {
    requestor = getAnnotationModel() match {
      case r: IProblemRequestor => Some(r)
      case _ => None
    }
    super.setDocument(document)
  }

  override def reconcile(dirtyRegion: DirtyRegion, subRegion: IRegion): Unit = {
    val isSpellingEnabled = store.getBoolean(ESpellingService.PREFERENCE_SPELLING_ENABLED)
    if (requestor.isDefined && isSpellingEnabled)
      super.reconcile(dirtyRegion, subRegion)
  }

  override def getAnnotationModel(): IAnnotationModel =
    Option(editor.getDocumentProvider()).map(_.getAnnotationModel(editor.getEditorInput())).orNull

  override def createSpellingProblemCollector(): ISpellingProblemCollector =
    requestor.map(r => new SpellingProblemCollector(r, getDocument(), editor)).orNull

  override def getContentType(): IContentType =
    contentType
}

/**
 * Forwards a [[SpellingProblem]] as a [[IProblem]] to a [[IProblemRequestor]].
 * This class is strongly coupled to [[ScalaSpellingReconcileStrategy]], which
 * doesn't extend this class in order to not leak the interface.
 *
 * The implementation is adopted from the private class
 * [[JavaSpellingReconcileStrategy.SpellingProblemCollector]].
 */
final class SpellingProblemCollector(requestor: IProblemRequestor, document: IDocument, editor: ITextEditor) extends ISpellingProblemCollector {

  override def accept(problem: SpellingProblem): Unit = {
    Option(editor.getEditorInput()) foreach { input =>
      val line = document.getLineOfOffset(problem.getOffset())+1
      val word = document.get(problem.getOffset(), problem.getLength())

      val (dictMatch, sentenceStart) = problem match {
        case p: JavaSpellingProblem =>
          p.isDictionaryMatch() -> p.isSentenceStart()
        case _ =>
          false -> false
      }

      requestor.acceptProblem(new CoreSpellingProblem(
          problem.getOffset(), problem.getOffset()+problem.getLength()-1,
          line, problem.getMessage(), word, dictMatch, sentenceStart,
          document, input.getName()))
    }
  }

  override def beginCollecting(): Unit =
    requestor.beginReporting()

  override def endCollecting(): Unit =
    requestor.endReporting()
}
