package scala.tools.eclipse.ui.actions

import scala.tools.eclipse.hyperlink.text.detector.BaseHyperlinkDetector
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.util.EditorUtils

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