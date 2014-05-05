package org.scalaide.util.internal.eclipse

import scala.tools.nsc.io.AbstractFile
import scala.tools.refactoring.common.TextChange

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.TextSelection
import org.eclipse.jface.text.link.LinkedModeModel
import org.eclipse.jface.text.link.LinkedModeUI
import org.eclipse.jface.text.link.LinkedPosition
import org.eclipse.jface.text.link.LinkedPositionGroup
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.IAnnotationModelExtension2
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.ltk.core.refactoring.TextFileChange
import org.eclipse.text.edits.MultiTextEdit
import org.eclipse.text.edits.RangeMarker
import org.eclipse.text.edits.ReplaceEdit
import org.eclipse.text.edits.UndoEdit
import org.eclipse.ui.IEditorPart
import org.eclipse.ui.IFileEditorInput
import org.eclipse.ui.IWorkbenchPage
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.part.FileEditorInput
import org.eclipse.ui.texteditor.ITextEditor
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.ui.internal.editor.ISourceViewerEditor
import org.scalaide.ui.internal.editor.InteractiveCompilationUnitEditor
import org.scalaide.util.internal.Utils.WithAsInstanceOfOpt

/**
 * Provides helper methods for the text editor of Eclipse, which is a GUI aware
 * class. This means that most methods of this object can only be called when an
 * UI environment is loaded - if this is not the case these methods will fail at
 * runtime.
 */
object EditorUtils {

  def openEditorAndApply[T](element: IJavaElement)(editor: IEditorPart => T): T =
    editor(JavaUI.openInEditor(element))

  /** Return the compilation unit open in the given editor.*/
  def getEditorCompilationUnit(editor: ITextEditor): Option[InteractiveCompilationUnit] = {
    editor match {
      case icuEditor: InteractiveCompilationUnitEditor =>
        Some(icuEditor.getInteractiveCompilationUnit)
      case _ =>
        None
    }
  }

  def getAnnotationsAtOffset(part: org.eclipse.ui.IEditorPart, offset: Int): Iterator[(Annotation, Position)] = {
    import scala.collection.JavaConverters._
    val model = JavaUI.getDocumentProvider.getAnnotationModel(part.getEditorInput)

    val annotations = model match {
      case null => Iterator.empty
      case am2: IAnnotationModelExtension2 => am2.getAnnotationIterator(offset, 1, true, true).asScala
      case _ => model.getAnnotationIterator.asScala
    }

    val annotationsWithPositions = annotations collect {
      case ann: Annotation => (ann, model.getPosition(ann))
    }

    val annotationsAtOffset = annotationsWithPositions filter {
      case (_, pos) => pos.includes(offset)
    }

    annotationsAtOffset
  }

  def textSelection2region(selection: ITextSelection): IRegion =
    new Region(selection.getOffset, selection.getLength)

  def getTextSelection(editor: ITextEditor): Option[ITextSelection] = {
    import org.scalaide.util.internal.Utils._
    for {
      workbenchSite <- Option(editor.getSite)
      provider <- Option(workbenchSite.getSelectionProvider)
      selection <- Option(provider.getSelection)
      textSelection <- selection.asInstanceOfOpt[ITextSelection]
    } yield textSelection
  }

  def activeWorkbenchWindow: Option[IWorkbenchWindow] =
    Option(PlatformUI.getWorkbench.getActiveWorkbenchWindow)

  def activePage(w: IWorkbenchWindow): Option[IWorkbenchPage] =
    Option(w.getActivePage)

  def activeEditor(p: IWorkbenchPage): Option[IEditorPart] =
    if (p.isEditorAreaVisible) Some(p.getActiveEditor) else None

  def textEditor(e: IEditorPart): Option[ISourceViewerEditor] =
    PartialFunction.condOpt(e) {
      case t: ISourceViewerEditor => t
    }

  def file(e: ITextEditor): Option[IFile] =
    PartialFunction.condOpt(e.getEditorInput()) {
      case f: IFileEditorInput => f.getFile
    }

  def selection(e: ITextEditor): Option[ITextSelection] =
    PartialFunction.condOpt(e.getSelectionProvider.getSelection) {
      case s: ITextSelection => s
    }

  def doWithCurrentEditor(block: ISourceViewerEditor => Unit): Unit = {
    withCurrentEditor { editor =>
      block(editor)
      None
    }
  }

  def withCurrentEditor[T](block: ISourceViewerEditor => Option[T]): Option[T] = for {
    w <- activeWorkbenchWindow
    p <- activePage(w)
    e <- activeEditor(p)
    t <- textEditor(e)
    b <- block(t)
  } yield b

  def withCurrentScalaSourceFile[T](block: ScalaSourceFile => T): Option[T] = {
    withCurrentEditor { textEditor =>
      file(textEditor) flatMap { file =>
        ScalaSourceFile.createFromPath(file.getFullPath.toString) map block
      }
    }
  }

  def withScalaFileAndSelection[T](block: (InteractiveCompilationUnit, ITextSelection) => Option[T]): Option[T] = {
    withCurrentEditor { textEditor =>
      EditorUtils.getEditorCompilationUnit(textEditor) flatMap { icu =>
        selection(textEditor) flatMap { selection =>
          block(icu, selection)
        }
      }
    }
  }

  def withScalaSourceFileAndSelection[T](block: (ScalaSourceFile, ITextSelection) => Option[T]): Option[T] = {
    withScalaFileAndSelection { (icu, selection) =>
      icu match {
        case ssf: ScalaSourceFile => block(ssf, selection)
        case _ => None
      }
    }
  }

  /**
   * Given an `ISourceViewer` it applies `f` on the underlying document's model.
   * If one of the involved components is `null`, even if `f` returns `null`, this
   * method returns `None`, otherwise the result of `f`.
   *
   * This method is UI independent.
   */
  def withDocument[A](sourceViewer: ISourceViewer)(f: IDocument => A): Option[A] =
    for {
      s <- Option(sourceViewer)
      d <- Option(s.getDocument)
      r <- Option(f(d))
    } yield r

  /** Creates a `TextFileChange` which always contains a `MultiTextEdit`. */
  def createTextFileChange(file: IFile, fileChanges: List[TextChange], saveAfter: Boolean = true): TextFileChange = {
    new TextFileChange(file.getName(), file) {

      val fileChangeRootEdit = new MultiTextEdit

      fileChanges map { change =>
        new ReplaceEdit(change.from, change.to - change.from, change.text)
      } foreach fileChangeRootEdit.addChild

      if (saveAfter) setSaveMode(TextFileChange.LEAVE_DIRTY)
      setEdit(fileChangeRootEdit)
    }
  }

  /**
   * Non UI logic that applies a `MultiTextEdit` and therefore the underlying document.
   * Returns a new text selection that describes the selection after the edit is applied.
   */
  def applyMultiTextEdit(document: IDocument, textSelection: ITextSelection, edit: MultiTextEdit): ITextSelection = {
    def selectionIsInManipulatedRegion(region: IRegion): Boolean = {
      val regionStart = region.getOffset
      val regionEnd = regionStart + region.getLength()
      val selectionStart = textSelection.getOffset()
      val selectionEnd = selectionStart + textSelection.getLength()

      selectionStart >= regionStart && selectionEnd <= regionEnd
    }

    val selectionCannotBeRetained = edit.getChildren map (_.getRegion) exists selectionIsInManipulatedRegion

    if (selectionCannotBeRetained) {
      // the selection overlaps the selected region, so we are on
      // our own in trying to the preserve the user's selection.
      if (edit.getOffset > textSelection.getOffset) {
        edit.apply(document)
        // if the edit starts after the start of the selection,
        // we just keep the current selection
        new TextSelection(document, textSelection.getOffset, textSelection.getLength)
      } else {
        // if the edit starts before the selection, we keep the
        // selection relative to the end of the document.
        val originalLength = document.getLength
        edit.apply(document)
        val modifiedLength = document.getLength
        new TextSelection(document, textSelection.getOffset + (modifiedLength - originalLength), textSelection.getLength())
      }

    } else {
      // Otherwise, we can track the selection and restore it after the refactoring.
      val currentPosition = new RangeMarker(textSelection.getOffset, textSelection.getLength)
      edit.addChild(currentPosition)
      edit.apply(document)
      new TextSelection(document, currentPosition.getOffset, currentPosition.getLength)
    }
  }

  /**
   * Applies a list of refactoring changes to a document and its underlying file.
   * In contrast to `applyChangesToFileWhileKeepingSelection` this method is UI
   * independent and therefore does not restore the correct selection in the editor.
   * Instead it returns the new selection which then can be handled afterwards.
   *
   * `None` is returned if an error occurs while writing to the underlying file.
   *
   * @param document The document the changes are applied to.
   * @param textSelection The currently selected area of the document.
   * @param file The file that we're currently editing (the document alone isn't enough because we need to get an IFile).
   * @param changes The changes that should be applied.
   * @param saveAfter Whether files should be saved after changes
   */
  def applyChangesToFile(
      document: IDocument,
      textSelection: ITextSelection,
      file: AbstractFile,
      changes: List[TextChange],
      saveAfter: Boolean = true): Option[ITextSelection] = {

    FileUtils.toIFile(file) map { f =>
      createTextFileChange(f, changes, saveAfter).getEdit match {
        // we know that it is a MultiTextEdit because we created it above
        case edit: MultiTextEdit =>
          applyMultiTextEdit(document, textSelection, edit)
      }
    }
  }

  /**
   * Applies a list of refactoring changes to a document. The current selection
   * (or just the caret position) is tracked and restored after applying the changes.
   *
   * In contrast to `applyChangesToFile` this method is UI dependent.
   *
   * @param document The document the changes are applied to.
   * @param textSelection The currently selected area of the document.
   * @param file The file that we're currently editing (the document alone isn't enough because we need to get an IFile).
   * @param changes The changes that should be applied.
   * @param saveAfter Whether files should be saved after changes
   */
  def applyChangesToFileWhileKeepingSelection(
      document: IDocument,
      textSelection: ITextSelection,
      file: AbstractFile,
      changes: List[TextChange],
      saveAfter: Boolean = true): Unit = {

    applyChangesToFile(document, textSelection, file, changes, saveAfter) foreach { selection =>
      doWithCurrentEditor { _.selectAndReveal(selection.getOffset(), selection.getLength()) }
    }
  }

  def applyRefactoringChangeToEditor(change: TextChange, editor: ITextEditor): UndoEdit = {
    val edit = new ReplaceEdit(change.from, change.to - change.from, change.text)
    val document = editor.getDocumentProvider.getDocument(editor.getEditorInput)
    edit.apply(document)
  }

  /**
   * Enters the editor in the LinkedModeUI with the given list of position groups.
   * Each position group is a list of positions with identical strings.
   * A position is given as an offset and the length.
   */
  def enterMultiLinkedModeUi(positionGroups: List[List[(Int, Int)]], selectFirst: Boolean): Unit =
    EditorUtils.doWithCurrentEditor { editor =>

      val model = new LinkedModeModel {
        positionGroups foreach { ps =>
          this addGroup new LinkedPositionGroup {
            val document = editor.getDocumentProvider.getDocument(editor.getEditorInput)
            ps foreach (p => addPosition(new LinkedPosition(document, p._1, p._2, 0)))
          }
        }
        forceInstall
      }

      val viewer = editor.getViewer

      // by default, an entire symbol is selected when entering linked mode; a nicer
      // behavior when renaming is to leave the cursor/selection as it was, so...

      // save the current selection
      val priorSelection = viewer.getSelectedRange()

      (new LinkedModeUI(model, viewer)).enter

      // restore the selection unless selecting the first instance of the symbol was desired
      if (!selectFirst)
        viewer.setSelectedRange(priorSelection.x, priorSelection.y)
    }

  /**
   * Enters the editor in the LinkedModeUI with the given list of positions.
   * A position is given as an offset and the length.
   */
  def enterLinkedModeUi(ps: List[(Int, Int)], selectFirst: Boolean): Unit =
    enterMultiLinkedModeUi(ps :: Nil, selectFirst)

  def findOrOpen(file: IFile): Option[IDocument] = {
    for (window <- activeWorkbenchWindow) yield {
      val page = window.getActivePage()
      val part = Option(page.findEditor(new FileEditorInput(file))).getOrElse(EditorUtility.openInEditor(file, true))
      page.bringToTop(part)
      JavaUI.getDocumentProvider().getDocument(part.getEditorInput());
    }
  }
}
