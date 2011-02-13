package scala.tools.eclipse.text.scala
import scala.tools.eclipse.contribution.weaving.jdt.IScalaCompletionProcessor
import org.eclipse.ui.IEditorPart

import org.eclipse.jface.text.contentassist.ContentAssistant

import org.eclipse.jdt.internal.ui.text.java.JavaCompletionProcessor

class ScalaCompletionProcessor(editor: IEditorPart, assistant: ContentAssistant, partition: String)
  extends JavaCompletionProcessor(editor, assistant, partition) with IScalaCompletionProcessor {

}