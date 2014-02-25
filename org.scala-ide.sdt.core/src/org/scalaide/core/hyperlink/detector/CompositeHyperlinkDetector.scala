package org.scalaide.core.hyperlink.detector

import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.ui.texteditor.ITextEditor
import org.scalaide.core.compiler.InteractiveCompilationUnit

class CompositeHyperlinkDetector(strategies: List[BaseHyperlinkDetector]) extends BaseHyperlinkDetector {
  override protected[detector] def runDetectionStrategy(scu: InteractiveCompilationUnit, textEditor: ITextEditor, currentSelection: IRegion): List[IHyperlink] =
    strategies flatMap { _.friendRunDetectionStrategy(scu, textEditor, currentSelection) }
}
