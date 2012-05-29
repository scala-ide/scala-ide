package scala.tools.eclipse.semantichighlighting.implicits

import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.AnnotationPainter
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.graphics.Color
import org.eclipse.jface.text.hyperlink.IHyperlink

object ImplicitAnnotation {
  final val ID = "scala.tools.eclipse.semantichighlighting.implicits.implicitConversionsOrArgsAnnotation"
}

abstract class ImplicitAnnotation(text: String) extends Annotation(ImplicitAnnotation.ID, /*isPersistent*/ false, text)

/** The source of this implicit conversion is computed lazily, only when needed. */
class ImplicitConversionAnnotation(_sourceLink: () => Option[IHyperlink], text: String) extends ImplicitAnnotation(text) {
  lazy val sourceLink = _sourceLink()
}

class ImplicitArgAnnotation(text: String) extends ImplicitAnnotation(text)

class ImpliticAnnotationTextStyleStrategy(var fontStyle: Int) extends AnnotationPainter.ITextStyleStrategy {

  // `applyTextStyle` is called by the AnnotatinPainter when the text is painted,
  // so we don't have to notify the `styleRange` of any changes to `fontStyle`.

  def applyTextStyle(styleRange: StyleRange, annotationColor: Color) {
    styleRange.fontStyle = fontStyle
    styleRange.underline = false
    styleRange.underlineColor = annotationColor
  }
}
