package org.scalaide.core.internal.hyperlink

import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility
import org.eclipse.jdt.internal.core.Openable
import org.eclipse.ui.texteditor.ITextEditor
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.jface.text.IRegion

/** An implementation of `IHyperlink` for Scala editors.
 *
 */
class ScalaHyperlink(openableOrUnit: AnyRef, pos: Int, len: Int, label: String, text: String, wordRegion: IRegion) extends IHyperlink {

  override def getHyperlinkRegion = wordRegion

  override def getTypeLabel = label

  override def getHyperlinkText = text

  override def open() = {
    /* This is a bad hack, but is currently needed to correctly navigate to sources attached to a binary file. */
    val part = openableOrUnit match {
      case editorInput: Openable => EditorUtility.openInEditor(editorInput, true)
      case unit: InteractiveCompilationUnit => EditorUtility.openInEditor(unit.workspaceFile, true)
      case _ => null
    }
    part match {
      case editor: ITextEditor => editor.selectAndReveal(pos, len)
      case _ =>
    }
  }
}