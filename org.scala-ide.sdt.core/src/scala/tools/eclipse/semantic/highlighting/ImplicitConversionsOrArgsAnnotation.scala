package scala.tools.eclipse
package semantic.highlighting

import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.AnnotationPainter
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.graphics.Color

class ImplicitConversionsOrArgsAnnotation(text: String, isPersistent: Boolean = false) extends Annotation(AnnotationsTypes.Implicits, isPersistent, text)

class ImplicitConversionsOrArgsTextStyleStrategy(var fFontStyle: Int) extends AnnotationPainter.ITextStyleStrategy {

  def applyTextStyle(styleRange: StyleRange, annotationColor: Color) {
    styleRange.fontStyle = fFontStyle
    styleRange.underline = false
    styleRange.underlineColor = annotationColor
  }
}
