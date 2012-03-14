package scala.tools.eclipse.properties.syntaxcolouring

import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses._
import scala.tools.eclipse.util.EclipseUtils._
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.jdt.ui.text.IColorManager
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.graphics.RGB
import org.eclipse.swt.graphics.Color
import org.eclipse.jface.text._
import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jdt.internal.ui.JavaPlugin

case class ScalaSyntaxClass(displayName: String, baseName: String, canBeDisabled: Boolean = false) {

  import ScalaSyntaxClasses._

  def enabledKey = baseName + ENABLED_SUFFIX
  def foregroundColourKey = baseName + FOREGROUND_COLOUR_SUFFIX
  def backgroundColourKey = baseName + BACKGROUND_COLOUR_SUFFIX
  def backgroundColourEnabledKey = baseName + BACKGROUND_COLOUR_ENABLED_SUFFIX
  def boldKey = baseName + BOLD_SUFFIX
  def italicKey = baseName + ITALIC_SUFFIX
  def underlineKey = baseName + UNDERLINE_SUFFIX

  def getTextAttribute(preferenceStore: IPreferenceStore): TextAttribute = {
    val styleInfo = getStyleInfo(preferenceStore)
    val style: Int = fullStyle(styleInfo)
    new TextAttribute(styleInfo.foreground, styleInfo.backgroundOpt.orNull, style)
  }

  private val colourManager = JavaPlugin.getDefault.getJavaTextTools.getColorManager

  def getStyleRange(preferenceStore: IPreferenceStore): StyleRange = {
    val styleRange = new StyleRange
    populateStyleRange(styleRange, preferenceStore)
    styleRange
  }

  def populateStyleRange(styleRange: StyleRange, preferenceStore: IPreferenceStore) =
    if (preferenceStore.getBoolean(enabledKey)) {
      val StyleInfo(enabled, foregroundColour, backgroundColourOpt, bold, italic, underline) = getStyleInfo(preferenceStore)
      val style = basicStyle(bold, italic)
      styleRange.fontStyle = style
      styleRange.foreground = foregroundColour
      styleRange.background = backgroundColourOpt.orNull
      styleRange.underline = underline
      styleRange.underlineColor = styleRange.foreground
    }

  case class StyleInfo(enabled: Boolean, foreground: Color, backgroundOpt: Option[Color], bold: Boolean, italic: Boolean, underline: Boolean)

  def getStyleInfo(preferenceStore: IPreferenceStore): StyleInfo = {
    val backgroundOpt =
      if (preferenceStore getBoolean backgroundColourEnabledKey)
        Some(colourManager.getColor(preferenceStore getColor backgroundColourKey))
      else
        None
    StyleInfo(
      preferenceStore getBoolean enabledKey,
      colourManager.getColor(preferenceStore getColor foregroundColourKey),
      backgroundOpt,
      preferenceStore getBoolean boldKey,
      preferenceStore getBoolean italicKey,
      preferenceStore getBoolean underlineKey)
  }

  private def basicStyle(bold: Boolean, italic: Boolean): Int = {
    var style = SWT.NORMAL
    if (bold) style |= SWT.BOLD
    if (italic) style |= SWT.ITALIC
    style
  }

  private def fullStyle(styleInfo: StyleInfo): Int = {
    val StyleInfo(_, _, _, bold, italic, underline) = styleInfo
    var style = basicStyle(bold, italic)
    if (underline) style |= TextAttribute.UNDERLINE
    style
  }

}
