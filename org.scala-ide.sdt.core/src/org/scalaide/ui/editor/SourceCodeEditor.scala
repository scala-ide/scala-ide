package org.scalaide.ui.editor

import org.scalaide.ui.internal.editor.ISourceViewerEditor
import org.scalaide.ui.internal.editor.InteractiveCompilationUnitEditor
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.ui.javaeditor.CompilationUnitDocumentProvider.ProblemAnnotation
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.Position
import org.eclipse.jface.text.source.IAnnotationModel
import org.eclipse.jface.text.source.IAnnotationModelExtension
import org.eclipse.jface.text.source.IAnnotationModelExtension2
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.ui.editors.text.TextEditor

trait SourceCodeEditor extends ISourceViewerEditor with InteractiveCompilationUnitEditor { self: TextEditor =>

  protected type UnderlyingCompilationUnit <: CompilationUnit

  protected val compilationUnitProvider: CompilationUnitProvider[UnderlyingCompilationUnit]

  protected def preferenceStore: IPreferenceStore

  @volatile
  private var previousAnnotations: List[ProblemAnnotation] = Nil

  private type IAnnotationModelExtended = IAnnotationModel with IAnnotationModelExtension with IAnnotationModelExtension2

  /** Return the annotation model associated with the current document. */
  private def annotationModel: IAnnotationModelExtended = getDocumentProvider.getAnnotationModel(getEditorInput).asInstanceOf[IAnnotationModelExtended]

  def updateErrorAnnotations(errors: List[IProblem]) {
    import scala.collection.JavaConverters._

    def position(p: IProblem) = new Position(p.getSourceStart, p.getSourceEnd - p.getSourceStart + 1)

    val newAnnotations = for (e <- errors) yield { (new ProblemAnnotation(e, null), position(e)) }

    annotationModel.replaceAnnotations(previousAnnotations.toArray, newAnnotations.toMap.asJava)
    previousAnnotations = newAnnotations.unzip._1
  }

  // FIXME: I see no reason for not caching the compilation unit. The only requirement I can see is that we need to handle document's swapping.
  //        This can be achieved by implementing a `ITextInputListener` and attach it to the `ISourceViewer` of `this` editor. This is needed to
  //        ensure that the `IDocument` hold by the compilation unit is always in synch with the one used by `this` editor.
  override def getInteractiveCompilationUnit(): UnderlyingCompilationUnit = compilationUnitProvider.fromEditor(this)

  override def getViewer: ISourceViewer = getSourceViewer
}
