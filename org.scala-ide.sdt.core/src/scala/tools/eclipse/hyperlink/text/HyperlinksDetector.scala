package scala.tools.eclipse.hyperlink
package text

import scala.Array.canBuildFrom
import scala.collection.mutable.ListBuffer
import scala.tools.eclipse.hyperlink.HyperlinksResolver
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.javaelements.ScalaSelectionEngine
import scala.tools.eclipse.javaelements.ScalaSelectionRequestor
import scala.tools.eclipse.semantichighlighting.implicits.ImplicitConversionAnnotation
import scala.tools.eclipse.ui.EditorUtils.getAnnotationsAtOffset
import scala.tools.eclipse.ui.EditorUtils.withEditor
import scala.tools.eclipse.ScalaWordFinder

import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor
import org.eclipse.jdt.internal.ui.javaeditor.JavaElementHyperlink
import org.eclipse.jdt.ui.actions.OpenAction
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextViewer
import org.eclipse.ui.texteditor.ITextEditor

class HyperlinksDetector extends AbstractHyperlinkDetector {

  private val resolver: HyperlinksResolver = new HyperlinksResolver

  def detectHyperlinks(viewer: ITextViewer, region: IRegion, canShowMultipleHyperlinks: Boolean): Array[IHyperlink] = {
    val textEditor = getAdapter(classOf[ITextEditor]).asInstanceOf[ITextEditor]
    detectHyperlinks(textEditor, region, canShowMultipleHyperlinks)
  }

  def detectHyperlinks(textEditor: ITextEditor, currentSelection: IRegion, canShowMultipleHyperlinks: Boolean): Array[IHyperlink] = {
    if (textEditor == null) // can be null if generated through ScalaPreviewerFactory
      null
    else
      EditorUtility.getEditorInputJavaElement(textEditor, false) match {
        case scu: ScalaCompilationUnit =>
          val wordRegion = ScalaWordFinder.findWord(scu.getContents, currentSelection.getOffset)

          val declarationHyperlinks = resolver.findHyperlinks(scu, wordRegion) match {
            case None => List()
            case Some(List()) => codeSelect(textEditor, wordRegion, scu)
            case Some(hyperlinks) => hyperlinks
          }

          val implicitHyperlinks = findHyperlinkToImplicit(scu, currentSelection.getOffset)

          (declarationHyperlinks ::: implicitHyperlinks).toArray

        case _ => null
      }
  }

  /**
   * Check if an {{{ImplicitConversionAnnotation}}} at the given {{{offset}}} exists in the
   * editor's annotation model.
   *
   * @return the {{{IHyperlink}}} to the implicit declaration, if one exists.
   */
  // FIXME: I quite dislike the current implementation, for the following reasons: 
  //        1) We go through all the editor's annotations to find if an implicit conversion is applied at the given {{{offset}}}.  
  //        2) Because we use the editor's annotation model, this functionality cannot be tested in a UI-less environment.
  private def findHyperlinkToImplicit(scu: ScalaCompilationUnit, offset: Int): List[IHyperlink] = {
    import scala.tools.eclipse.semantichighlighting.implicits.ImplicitConversionAnnotation
    import scala.tools.eclipse.ui.EditorUtils.{ withEditor, getAnnotationsAtOffset }

    var hyperlinks = List[IHyperlink]()

    withEditor(scu) { editor =>
      for ((ann, pos) <- getAnnotationsAtOffset(editor, offset)) ann match {
        case a: ImplicitConversionAnnotation if a.sourceLink.isDefined =>
          hyperlinks = a.sourceLink.get :: hyperlinks
        case _ => ()
      }
    }

    hyperlinks
  }

  //Default path used for selecting.
  private def codeSelect(textEditor: ITextEditor, wordRegion: IRegion, scu: ScalaCompilationUnit): List[IHyperlink] = {
    try {
      val environment = scu.newSearchableEnvironment()
      val requestor = new ScalaSelectionRequestor(environment.nameLookup, scu)
      val engine = new ScalaSelectionEngine(environment, requestor, scu.getJavaProject.getOptions(true))
      val offset = wordRegion.getOffset
      engine.select(scu, offset, offset + wordRegion.getLength - 1)
      val elements = requestor.getElements.toList

      lazy val qualify = elements.length > 1
      lazy val openAction = new OpenAction(textEditor.asInstanceOf[JavaEditor])
      elements.map(new JavaElementHyperlink(wordRegion, openAction, _, qualify))
    } catch {
      case _ => List[IHyperlink]()
    }
  }
}