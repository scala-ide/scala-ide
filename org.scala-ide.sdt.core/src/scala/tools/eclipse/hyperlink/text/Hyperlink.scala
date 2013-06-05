package scala.tools.eclipse.hyperlink.text

import org.eclipse.core.resources.IFile
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jdt.internal.core.Openable
import scala.tools.eclipse.InteractiveCompilationUnit

/** A creator of resolved hyperlinks.
 *
 *  Most often you will use a `HyperlinkFactory`, that resolves compiler `Symbols` to hyperlinks.
 *  This is just a facade to the concrete implementation of the `IHyperlink` interface.
 */
object Hyperlink {

  type Factory = (AnyRef, Int, Int, String, IRegion) => IHyperlink

  def withText(name: String)(openableOrUnit: AnyRef, pos: Int, len: Int, label: String, wordRegion: IRegion): IHyperlink =
    new ScalaHyperlink(openableOrUnit, pos, len, label, text = name, wordRegion)

  private class ScalaHyperlink(openableOrUnit: AnyRef, pos: Int, len: Int, label: String, text: String, wordRegion: IRegion) extends IHyperlink {
    def getHyperlinkRegion = wordRegion
    def getTypeLabel = label
    def getHyperlinkText = text
    def open = {
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
}

