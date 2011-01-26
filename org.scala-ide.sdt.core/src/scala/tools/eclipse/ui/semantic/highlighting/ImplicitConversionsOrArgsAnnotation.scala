package scala.tools.eclipse.ui.semantic.highlighting

import org.eclipse.jface.text.source.Annotation
import org.eclipse.jface.text.source.IAnnotationPresentation
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.GC
import org.eclipse.swt.graphics.Image
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.graphics.Rectangle
import org.eclipse.swt.widgets.Canvas
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.graphics.Color
import org.eclipse.jface.text.source.AnnotationPainter
import org.eclipse.jdt.internal.ui.javaeditor.IJavaAnnotation
import org.eclipse.jdt.core.ICompilationUnit
import scala.tools.eclipse.util.AnnotationsTypes

class ImplicitConversionsOrArgsAnnotation(cu: ICompilationUnit, text: String, isPersistent: Boolean = false)
  extends Annotation(AnnotationsTypes.Implicits, isPersistent, text) with IJavaAnnotation {

  //override def isPersistent() = true
  override def isMarkedDeleted() = false
  override def getText = super.getText()
  override def hasOverlay() = false
  override def getOverlay() = null
  override def getOverlaidIterator() = null
  override def addOverlaid(annotation: IJavaAnnotation) {}
  override def removeOverlaid(annotation: IJavaAnnotation) {}
  override def isProblem() = false
  override def getCompilationUnit() = cu
  override def getArguments() = null
  override def getId() = 0 //XXX: hacking for jdt's org.eclipse.jdt.internal.ui.text.correction.JavaCorrectionProcessor
  override def getMarkerType() = null

}

class ImplicitConversionsOrArgsTextStyleStrategy(var fFontStyle: Int) extends AnnotationPainter.ITextStyleStrategy {

  def applyTextStyle(styleRange: StyleRange, annotationColor: Color) {
    styleRange.fontStyle = fFontStyle
    styleRange.underline = false
    styleRange.underlineColor = annotationColor
  }

}