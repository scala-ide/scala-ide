package scala.tools.eclipse.semantichighlighting.implicits

import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.AnnotationPainter
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.graphics.Color

object ImplicitConversionsOrArgsAnnotation {

  final val ID = "scala.tools.eclipse.semantichighlighting.implicits.implicitConversionsOrArgsAnnotation"

}

class ImplicitConversionsOrArgsAnnotation(text: String, isPersistent: Boolean = false)
  extends Annotation(ImplicitConversionsOrArgsAnnotation.ID, isPersistent, text)

class ImplicitConversionsOrArgsTextStyleStrategy(var fontStyle: Int) extends AnnotationPainter.ITextStyleStrategy {

  // `applyTextStyle` is called by the AnnotatinPainter when the text is painted,
  // so we don't have to notify the `styleRange` of any changes to `fontStyle`.

  def applyTextStyle(styleRange: StyleRange, annotationColor: Color) {
    styleRange.fontStyle = fontStyle
    styleRange.underline = false
    styleRange.underlineColor = annotationColor
  }
}
