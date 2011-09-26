package scala.tools.eclipse.properties

import scala.tools.eclipse.properties.ScalaSyntaxClasses._
import org.eclipse.jdt.ui.text.IColorManager
import org.eclipse.swt.graphics.RGB
import org.eclipse.swt.SWT
import org.eclipse.jface.text._
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.jface.preference.IPreferenceStore

case class ScalaSyntaxClass(displayName: String, baseName: String) {

  import ScalaSyntaxClasses._
  
  def colourKey = baseName + COLOUR_SUFFIX
  def boldKey = baseName + BOLD_SUFFIX
  def italicKey = baseName + ITALIC_SUFFIX
  def underlineKey = baseName + UNDERLINE_SUFFIX
  def strikethroughKey = baseName + STRIKETHROUGH_SUFFIX

  def getTextAttribute(colorManager: IColorManager, preferenceStore: IPreferenceStore): TextAttribute = {
    val colour = colorManager.getColor(PreferenceConverter.getColor(preferenceStore, colourKey))
    val style: Int = makeStyle(preferenceStore.getBoolean(boldKey), preferenceStore.getBoolean(italicKey),
      preferenceStore.getBoolean(strikethroughKey), preferenceStore.getBoolean(underlineKey))
    val backgroundColour = null
    new TextAttribute(colour, backgroundColour, style)
  }

  private def makeStyle(bold: Boolean, italic: Boolean, strikethrough: Boolean, underline: Boolean): Int = {
    var style = SWT.NORMAL
    if (bold) style |= SWT.BOLD
    if (italic) style |= SWT.ITALIC
    if (strikethrough) style |= TextAttribute.STRIKETHROUGH
    if (underline) style |= TextAttribute.UNDERLINE
    style
  }

}
