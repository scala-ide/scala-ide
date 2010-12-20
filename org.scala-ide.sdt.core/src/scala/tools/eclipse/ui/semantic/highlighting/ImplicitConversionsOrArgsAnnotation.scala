package scala.tools.eclipse.ui.semantic.highlighting

import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationPresentation;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.graphics.Color
import org.eclipse.jface.text.source.AnnotationPainter;
import org.eclipse.jdt.internal.ui.javaeditor.IJavaAnnotation
import org.eclipse.jdt.core.ICompilationUnit

class ImplicitConversionsOrArgsAnnotation(cu: ICompilationUnit, kind: String, isPersistent: Boolean, text: String)
  extends Annotation(kind, isPersistent, text) with IJavaAnnotation {
	
	//
    override def getType(): String = ImplicitConversionsOrArgsAnnotation.KIND
	override def isPersistent() = true
	override def isMarkedDeleted() = false
	override def getText = super.getText()
	override def hasOverlay() = false
	override def getOverlay() = null
	override def getOverlaidIterator() = null
	override def addOverlaid(annotation: IJavaAnnotation){}
    override def removeOverlaid(annotation: IJavaAnnotation){}
	override def isProblem() = false
	override def getCompilationUnit() = cu
	override def getArguments() = null
	override def getId() = 0 //XXX: hacking for jdt's org.eclipse.jdt.internal.ui.text.correction.JavaCorrectionProcessor
	override def getMarkerType() = null
	
}

object ImplicitConversionsOrArgsAnnotation {
	final val KIND = "scala.tools.eclipse.ui.semantic.highlighting.implicitConversionsOrArgsAnnotation" 
}


class ImplicitConversionsOrArgsTextStyleStrategy(var fUnderlineStyle: Int, var fFontStyle: Int) extends AnnotationPainter.ITextStyleStrategy {
	
    def applyTextStyle(styleRange: StyleRange, annotationColor: Color) {
    	    styleRange.fontStyle = fFontStyle
    	    if (fUnderlineStyle==8) {
    	    	styleRange.underline= false
    	    	return
    	    }
    	    styleRange.underline= true
			styleRange.underlineStyle= fUnderlineStyle 
			styleRange.underlineColor= annotationColor 
	}
    
    def setUnderlineStyle(fus: Int) {
    	fUnderlineStyle = fus
    }
	
    def setFontStyle(ffs: Int) {
    	fFontStyle = ffs
    }
    
}