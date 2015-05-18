package org.scalaide.ui.internal.actions.hyperlinks

import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor
import org.eclipse.jdt.ui.actions.OpenAction
import org.scalaide.core.internal.hyperlink.BaseHyperlinkDetector
import org.scalaide.core.internal.hyperlink.DeclarationHyperlinkDetector

class HyperlinkOpenAction(editor: JavaEditor) extends OpenAction(editor) with HyperlinkOpenActionStrategy {

  override protected val detectionStrategy: BaseHyperlinkDetector = DeclarationHyperlinkDetector()

  override def run(): Unit = { openHyperlink(editor) }
}
