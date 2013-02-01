package scala.tools.eclipse

import org.eclipse.jdt.internal.ui.text.JavaPresentationReconciler
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.TextPresentation

/** It is really sad that we need to inherit from `JavaPresentationReconciler` (and not simply `PresentationReconciler`).
 *  The reason is that the JDT `ClassFileEditor` (which is re-used by the Scala IDE, for instance when opening a type 
 *  that comes from a Scala jar), during initialization makes a call to `JavaEditor.installSemanticHighlighting`, which 
 *  expects that the presentation reconciler returned by `ScalaSourceViewerConfiguration.getPresentationReconciler` is 
 *  a subtype of `JavaPresentationReconciler` (the assumption is made real via an unsafe cast).
 *  
 *  There is an additional interesting fact. To prevent the `JavaEditor` to install semantic highlighting on Scala sources 
 *  an aspect pointcut was defined in `ScalaEditorPreferencesAspect`. It turns out this is not needed, because in the 
 *  `ScalaSourceFileEditor` (which extends the `JavaEditor`) we can noop the call to `JavaEditor.installSemanticHighlighting`.
 *  Unfortunately, the same cannot be done for the `ClassFileEditor, because we don't currently provide a custom implementation.
 */ 
class ScalaPresentationReconciler extends JavaPresentationReconciler {

  @volatile private var lastDocument: IDocument = null

  override def createRepairDescription(damage: IRegion, document: IDocument): TextPresentation = {
    if (document != lastDocument) {
      setDocumentToDamagers(document)
      setDocumentToRepairers(document)
      lastDocument = document
    }
    createPresentation(damage, document)
  }
}