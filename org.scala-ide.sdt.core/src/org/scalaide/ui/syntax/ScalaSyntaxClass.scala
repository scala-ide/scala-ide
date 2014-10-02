package org.scalaide.ui.syntax

import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.TextAttribute
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.RGB
import org.scalaide.util.internal.eclipse.EclipseUtils.PimpedPreferenceStore

import org.scalaide.util.internal.ui.DisplayThread

/** Represent a class of element in the Scala syntax coloring support.
 *  [[ScalaSyntaxClasses]] contains the list of classes of element recognized by the IDE.
 */
case class ScalaSyntaxClass(displayName: String, baseName: String, canBeDisabled: Boolean = false, hasForegroundColor: Boolean = true) {

  import ScalaSyntaxClass._

  /** The preference key for enabling syntax coloring for this class.
   */
  def enabledKey = baseName + ENABLED_SUFFIX

  /** The preference key for the foreground color for this class.
   */
  def foregroundColorKey = baseName + FOREGROUND_COLOUR_SUFFIX

  /** The preference key for the background color for this class.
   */
  def backgroundColorKey = baseName + BACKGROUND_COLOUR_SUFFIX

  /** The preference key for enabling background coloring for this class.
   */
  def backgroundColorEnabledKey = baseName + BACKGROUND_COLOUR_ENABLED_SUFFIX

  /** The preference key for enabling the bold modifier for this class.
   */
  def boldKey = baseName + BOLD_SUFFIX

  /** The preference key for enabling the italic modifier for this class.
   */
  def italicKey = baseName + ITALIC_SUFFIX

  /** The preference key for enabling the underline modifier for this class.
   */
  def underlineKey = baseName + UNDERLINE_SUFFIX

  /** Returns the [[TextAttribute]] for this class, according to the given
   *  preference store.
   *
   *  @param preferenceStore the preference store to extract the configuration from.
   */
  def getTextAttribute(preferenceStore: IPreferenceStore): TextAttribute = {
    new TextAttribute(getForegroundColor(preferenceStore), getBackgroundColor(preferenceStore), computeStyle(preferenceStore))
  }

  /** Returns the `true` if syntax highlighting is enabled for this class, according to the given
   *  preference store.
   *
   *  @param preferenceStore the preference store to extract the configuration from.
   */
  def enabled(preferenceStore: IPreferenceStore): Boolean =
    preferenceStore getBoolean enabledKey

  /** Returns the style flags for this class, according to the given
   *  preference store.
   */
  private def computeStyle(preferenceStore: IPreferenceStore): Int = {
    var style = SWT.NORMAL
    if (preferenceStore getBoolean boldKey) style |= SWT.BOLD
    if (preferenceStore getBoolean italicKey) style |= SWT.ITALIC
    if (preferenceStore getBoolean underlineKey) style |= TextAttribute.UNDERLINE
    style
  }

  /** Returns the foreground color for this class, or `null` if foreground color should not be applied (Eclipse API convention).
   */
  private def getForegroundColor(preferenceStore: IPreferenceStore): Color = {
    if (hasForegroundColor) {
      getColor(preferenceStore getColor foregroundColorKey)
    } else {
      null // Eclipse API convention
    }
  }

  /** Returns the background color for this class, or `null` if foreground color should not be applied (Eclipse API convention).
   */
  private def getBackgroundColor(preferenceStore: IPreferenceStore): Color = {
    if (preferenceStore getBoolean backgroundColorEnabledKey) {
      getColor(preferenceStore getColor backgroundColorKey)
    } else {
      null // Eclipse API convention
    }
  }

  /** Returns a platform [[Color]], for the given color descriptor.
   *  It requires a synchronous call on the UI thread to create the color instance.
   */
  private def getColor(colorDesc: RGB): Color = {
    val colorManager = JavaPlugin.getDefault.getJavaTextTools.getColorManager
    var color: Color = null
    DisplayThread.syncExec {
      color = colorManager.getColor(colorDesc)
    }
    color
  }

}

object ScalaSyntaxClass {

  private val ENABLED_SUFFIX = ".enabled"
  private val FOREGROUND_COLOUR_SUFFIX = ".colour"
  private val BACKGROUND_COLOUR_SUFFIX = ".backgroundColour"
  private val BACKGROUND_COLOUR_ENABLED_SUFFIX = ".backgroundColourEnabled"
  private val BOLD_SUFFIX = ".bold"
  private val ITALIC_SUFFIX = ".italic"
  private val UNDERLINE_SUFFIX = ".underline"

  /** Syntax category, to order syntax classes.
   *  Used when displaying the classes in the preference pages.
   */
  case class Category(name: String, children: List[ScalaSyntaxClass])

}
