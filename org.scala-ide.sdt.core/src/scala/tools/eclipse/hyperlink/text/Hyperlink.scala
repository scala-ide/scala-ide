package scala.tools.eclipse.hyperlink.text

import org.eclipse.jdt.internal.core.Openable
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.jface.text.IRegion
import org.eclipse.ui.texteditor.ITextEditor

/** A creator of resolved hyperlinks.
 *  
 *  Most often you will use a `HyperlinkFactory`, that resolves compiler `Symbols` to hyperlinks.
 *  This is just a facade to the concrete implementation of the `IHyperlink` interface.
 */
object Hyperlink {
 
  type Factory = (Openable, Int, Int, String, IRegion) => IHyperlink

  def withText(name: String)(file: Openable, pos: Int, len: Int, label: String, wordRegion: IRegion): IHyperlink = 
    new ScalaHyperlink(file, pos, len, label, text = name, wordRegion)
  
  private class ScalaHyperlink(file: Openable, pos: Int, len: Int, label: String, text: String, wordRegion: IRegion) extends IHyperlink {
    def getHyperlinkRegion = wordRegion
    def getTypeLabel = label
    def getHyperlinkText = text
    def open = {
      EditorUtility.openInEditor(file, true) match {
        case editor: ITextEditor => editor.selectAndReveal(pos, len)
        case _ =>
      }
    }
  }
}

