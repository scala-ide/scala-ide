package org.scalaide.ui.internal.editor.decorators.bynameparams

import org.eclipse.jface.text.source.Annotation
import org.scalaide.ui.editor.ScalaEditorAnnotation

object CallByNameParamAtCreationAnnotation {
  val ID = "scala.tools.eclipse.semantichighlighting.callByNameParam.creationAnnotation"
}

final class CallByNameParamAtCreationAnnotation(text: String)
  extends Annotation(CallByNameParamAtCreationAnnotation.ID, false, text) with ScalaEditorAnnotation {

}
