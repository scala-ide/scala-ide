package scala.tools.eclipse
package editor.text

import org.eclipse.jface.text.source.Annotation
import org.eclipse.jdt.core.compiler.IProblem
import scala.tools.eclipse.util.AnnotationsTypes

class ProblemAnnotation(v : IProblem) extends Annotation(AnnotationsTypes.Problems, false, v.getMessage) {
//  override def isProblem() = true
//  override def getArguments() = v.getArguments
//  override def getId() = v.getID //XXX: hacking for jdt's org.eclipse.jdt.internal.ui.text.correction.JavaCorrectionProcessor
//  override def getMarkerType() = null

}