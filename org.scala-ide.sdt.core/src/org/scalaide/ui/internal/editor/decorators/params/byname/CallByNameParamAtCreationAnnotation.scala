package org.scalaide.ui.internal.editor.decorators.params.byname

import org.eclipse.jface.text.source.Annotation
import org.scalaide.ui.editor.ScalaEditorAnnotation

object CallByNameParamAtCreationAnnotation {
  val ID = "scala.tools.eclipse.semantichighlighting.callByNameParam.creationAnnotation"
}

class CallByNameParamAtCreationAnnotation(text: String)
  extends Annotation(CallByNameParamAtCreationAnnotation.ID, false, text) with ScalaEditorAnnotation {

}
