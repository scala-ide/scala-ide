/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.jdt.core.{ ICodeAssist, IJavaElement, JavaModelException }
import org.eclipse.jdt.internal.ui.javaeditor.{ EditorUtility, JavaElementHyperlink, JavaElementHyperlinkDetector }
import org.eclipse.jdt.ui.actions.SelectionDispatchAction
import org.eclipse.jface.text.{ ITextViewer, IRegion }
import org.eclipse.jface.text.hyperlink.{ IHyperlink, IHyperlinkDetector }
import org.eclipse.ui.texteditor.ITextEditor;

class ScalaHyperlinkDetector extends JavaElementHyperlinkDetector {
  override def detectHyperlinks(tv : ITextViewer, region : IRegion, canShowMultiple : Boolean) : Array[IHyperlink] = {
    
    val textEditor = getAdapter(classOf[ITextEditor]).asInstanceOf[ITextEditor]
    if (region == null || !textEditor.isInstanceOf[ScalaEditor])
      return null

    val openAction = textEditor.getAction("OpenEditor")
    if (!openAction.isInstanceOf[SelectionDispatchAction])
      return null

    val offset = region.getOffset
    val input = EditorUtility.getEditorInputJavaElement(textEditor, false)
    if (input == null)
      return null

    try {
      val editorInput = textEditor.getEditorInput
      val project = ScalaPlugin.plugin.getScalaProject(editorInput)
      val document = textEditor.getDocumentProvider.getDocument(editorInput)
      val wordRegion = ScalaWordFinder.findWord(document, offset)
      println("detectHyperlinks: wordRegion = "+wordRegion)
      
      if (wordRegion == null || wordRegion.getLength == 0)
        return null

      def isLinkable(element : IJavaElement) = {
        import IJavaElement._
        element.getElementType match {
          case PACKAGE_DECLARATION | PACKAGE_FRAGMENT | PACKAGE_FRAGMENT_ROOT | JAVA_PROJECT | JAVA_MODEL => false
          case _ => true
        }
      }

      val elements = input.asInstanceOf[ICodeAssist].codeSelect(wordRegion.getOffset, wordRegion.getLength).filter(e => e != null && isLinkable(e))
      if (elements.length == 0)
        return null

      val qualify = elements.length > 1
      elements.map(new JavaElementHyperlink(wordRegion, openAction.asInstanceOf[SelectionDispatchAction], _, qualify))
    } catch {
      case ex : JavaModelException => null
    }
  }
}
