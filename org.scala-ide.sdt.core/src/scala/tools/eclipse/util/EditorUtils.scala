package scala.tools.eclipse.util

import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextSelection
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.IAnnotationModelExtension2
import org.eclipse.ui.IEditorPart
import org.eclipse.ui.texteditor.ITextEditor

import scala.collection.JavaConverters.asScalaIteratorConverter
import scala.tools.eclipse.InteractiveCompilationUnit
import scala.tools.eclipse.util.Utils.any2optionable

// FIXME: This should be merged with merged with {{{scala.tools.eclipse.refactoring.EditorHelpers}}}
object EditorUtils {

  def openEditorAndApply[T](element: IJavaElement)(editor: IEditorPart => T): T =
    editor(org.eclipse.jdt.ui.JavaUI.openInEditor(element))

  /** Return the compilation unit open in the given editor.
   *
   *  It first tries to retrieve a compilation unit based on `IJavaElement` (a `ScalaCompilationUnit`),
   *  otherwise it tries to get an adapter from the editor input to `InteractiveCompilationUnit`. Other
   *  plugins may register adapters through `IAdapterManager` to allow other kinds of Scala-based units
   *  to be retrieved this way.
   */
  def getEditorScalaInput(editor: ITextEditor): InteractiveCompilationUnit = {
    val input = editor.getEditorInput
    input.getAdapter(classOf[IJavaElement]) match {
      case unit: InteractiveCompilationUnit => unit
      case _  => input.getAdapter(classOf[InteractiveCompilationUnit]).asInstanceOf[InteractiveCompilationUnit]
    }
  }

  def getAnnotationsAtOffset(part: org.eclipse.ui.IEditorPart, offset: Int): Iterator[(Annotation, Position)] = {
    val model = org.eclipse.jdt.ui.JavaUI.getDocumentProvider.getAnnotationModel(part.getEditorInput)

    val annotations = model match {
      case null                            => Iterator.empty
      case am2: IAnnotationModelExtension2 => am2.getAnnotationIterator(offset, 1, true, true).asScala
      case _                               => model.getAnnotationIterator.asScala
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
    for{ 
      workbenchSite <- Option(editor.getSite)
      provider <- Option(workbenchSite.getSelectionProvider)
      selection <- Option(provider.getSelection)
      textSelection <- selection.asInstanceOfOpt[ITextSelection]
    } yield textSelection
  }
}