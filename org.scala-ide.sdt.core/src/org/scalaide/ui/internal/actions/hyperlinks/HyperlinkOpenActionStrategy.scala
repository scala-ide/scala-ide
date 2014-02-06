package org.scalaide.ui.internal.actions.hyperlinks

import org.scalaide.core.hyperlink.detector.BaseHyperlinkDetector
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.util.internal.eclipse.EditorUtils
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor
import org.eclipse.jface.text.ITextSelection

trait HyperlinkOpenActionStrategy {
  protected def detectionStrategy: BaseHyperlinkDetector

  protected def openHyperlink(editor: JavaEditor) {
    getTextSelection(editor) map { selection =>
      withScalaCompilatioUnit(editor) { scu =>
        scu.followReference(detectionStrategy, editor, selection)
      }
    }
  }

  private def withScalaCompilatioUnit[T](editor: JavaEditor)(f: ScalaCompilationUnit => T): Option[T] = {
    val inputJavaElement = EditorUtility.getEditorInputJavaElement(editor, false)
    Option(inputJavaElement) map (_.asInstanceOf[ScalaCompilationUnit]) map (f)
  }

  protected def isEnabled(editor: JavaEditor): Boolean = getTextSelection(editor) map { textSelection =>
    val region = EditorUtils.textSelection2region(textSelection)
    detectionStrategy.detectHyperlinks(editor, region, canShowMultipleHyperlinks = false) != null
  } getOrElse(false)

  private def getTextSelection(editor: JavaEditor): Option[ITextSelection] = EditorUtils.getTextSelection(editor)
}
