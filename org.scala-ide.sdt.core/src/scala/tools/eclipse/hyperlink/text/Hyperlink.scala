package scala.tools.eclipse.hyperlink.text

import org.eclipse.jdt.internal.core.Openable
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.jface.text.IRegion
import org.eclipse.ui.texteditor.ITextEditor

private[hyperlink] object Hyperlink {
 
  type Factory = (Openable, Int, Int, String, IRegion) => IHyperlink

  def toDeclaration(file: Openable, pos: Int, len: Int, label: String, wordRegion: IRegion): IHyperlink =
    new OpenDeclaration(file, pos, len, label, wordRegion)

  def toImplicit(file: Openable, pos: Int, len: Int, text: String, wordRegion: IRegion): IHyperlink =
    new OpenImplicit(file, pos, len, text, wordRegion)

  abstract class Hyperlink(file: Openable, pos: Int, len: Int, label: String, text: String, wordRegion: IRegion) extends IHyperlink {
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

  class OpenDeclaration(file: Openable, pos: Int, len: Int, label: String, region: IRegion)
    extends Hyperlink(file, pos, len, label, text = "Open Declaration", region)

  class OpenImplicit(file: Openable, pos: Int, len: Int, label: String, region: IRegion)
    extends Hyperlink(file, pos, len, label, text = "Open Implicit", region)
}

