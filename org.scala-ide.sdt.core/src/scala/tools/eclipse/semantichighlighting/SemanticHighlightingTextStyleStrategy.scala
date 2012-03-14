package scala.tools.eclipse.semantichighlighting

import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClass
import scala.tools.eclipse.ScalaPlugin

import org.eclipse.jface.text.source.AnnotationPainter
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.graphics.Color

class SemanticHighlightingTextStyleStrategy(syntaxClass: ScalaSyntaxClass, deprecated: Boolean)
  extends AnnotationPainter.ITextStyleStrategy {

  def applyTextStyle(styleRange: StyleRange, annotationColor: Color) {
    syntaxClass.populateStyleRange(styleRange, ScalaPlugin.prefStore)
    styleRange.strikeout = deprecated
  }

}