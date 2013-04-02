package scala.tools.eclipse.properties.syntaxcolouring

import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses._
import scala.tools.eclipse.util.SWTUtils.fnToPropertyChangeListener
import scala.tools.eclipse.ScalaPlugin

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.resource.StringConverter
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.swt.graphics.RGB

class ColourPreferenceInitializer extends AbstractPreferenceInitializer {

  override def initializeDefaultPreferences() {
    if (!ScalaPlugin.plugin.headlessMode) {
      doInitializeDefaultPreferences()
    }
  }
  
  private def doInitializeDefaultPreferences() {
    val scalaPrefStore = ScalaPlugin.prefStore

    scalaPrefStore.setDefault(ENABLE_SEMANTIC_HIGHLIGHTING, true)
    scalaPrefStore.setDefault(USE_SYNTACTIC_HINTS, true)
    scalaPrefStore.setDefault(STRIKETHROUGH_DEPRECATED, true)

    setDefaultsForSyntaxClasses(scalaPrefStore)
  }

  private def setDefaultsForSyntaxClasses(implicit scalaPrefStore: IPreferenceStore) {
    // Scala syntactic
    setDefaultsForSyntaxClass(SINGLE_LINE_COMMENT, new RGB(63, 127, 95))
    setDefaultsForSyntaxClass(MULTI_LINE_COMMENT, new RGB(63, 127, 95))
    setDefaultsForSyntaxClass(SCALADOC, new RGB(63, 95, 191))
    setDefaultsForSyntaxClass(SCALADOC_CODE_BLOCK, new RGB(63, 95, 191), italic = true)
    setDefaultsForSyntaxClass(SCALADOC_ANNOTATION, new RGB(63, 95, 191), bold = true)
    setDefaultsForSyntaxClass(SCALADOC_MACRO, new RGB(63, 95, 191), bold = true)
    setDefaultsForSyntaxClass(KEYWORD, new RGB(127, 0, 85), bold = true)
    setDefaultsForSyntaxClass(STRING, new RGB(122, 170, 28))
    setDefaultsForSyntaxClass(CHARACTER, new RGB(122, 170, 28))
    setDefaultsForSyntaxClass(MULTI_LINE_STRING, new RGB(122, 170, 28))
    setDefaultsForSyntaxClass(DEFAULT, new RGB(0, 0, 0))
    setDefaultsForSyntaxClass(OPERATOR, new RGB(0, 0, 0))
    setDefaultsForSyntaxClass(BRACKET, new RGB(0, 0, 0))
    setDefaultsForSyntaxClass(RETURN, new RGB(127, 0, 85), bold = true)
    setDefaultsForSyntaxClass(BRACKET, new RGB(0, 0, 0))
    setDefaultsForSyntaxClass(NUMBER_LITERAL, new RGB(196, 140, 255))
    setDefaultsForSyntaxClass(ESCAPE_SEQUENCE, new RGB(196, 140, 255))
    setDefaultsForSyntaxClass(SYMBOL, new RGB(173, 142, 0))

    // XML, see org.eclipse.wst.xml.ui.internal.preferences.XMLUIPreferenceInitializer
    setDefaultsForSyntaxClass(XML_COMMENT, new RGB(63, 85, 191))
    setDefaultsForSyntaxClass(XML_ATTRIBUTE_VALUE, new RGB(42, 0, 255), italic = true)
    setDefaultsForSyntaxClass(XML_ATTRIBUTE_NAME, new RGB(127, 0, 127))
    setDefaultsForSyntaxClass(XML_ATTRIBUTE_EQUALS, new RGB(0, 0, 0))
    setDefaultsForSyntaxClass(XML_TAG_DELIMITER, new RGB(0, 128, 128))
    setDefaultsForSyntaxClass(XML_TAG_NAME, new RGB(63, 127, 127))
    setDefaultsForSyntaxClass(XML_PI, new RGB(0, 128, 128))
    setDefaultsForSyntaxClass(XML_CDATA_BORDER, new RGB(0, 128, 128))

    // Scala semantic:
    setDefaultsForSyntaxClass(ANNOTATION, new RGB(222, 0, 172), enabled = true)
    setDefaultsForSyntaxClass(CASE_CLASS, new RGB(162, 46, 0), bold = true, enabled = false)
    setDefaultsForSyntaxClass(CASE_OBJECT, new RGB(162, 46, 0), bold = true, enabled = false)
    setDefaultsForSyntaxClass(CLASS, new RGB(50, 147, 153), enabled = false)
    setDefaultsForSyntaxClass(LAZY_LOCAL_VAL, new RGB(94, 94, 255), enabled = true)
    setDefaultsForSyntaxClass(LAZY_TEMPLATE_VAL, new RGB(0, 0, 192), enabled = true)
    setDefaultsForSyntaxClass(LOCAL_VAL, new RGB(94, 94, 255), enabled = true)
    setDefaultsForSyntaxClass(LOCAL_VAR, new RGB(255, 94, 94), enabled = true)
    setDefaultsForSyntaxClass(METHOD, new RGB(76, 76, 76), italic = true, enabled = false)
    setDefaultsForSyntaxClass(PARAM, new RGB(100, 0, 103), enabled = false)
    setDefaultsForSyntaxClass(TEMPLATE_VAL, new RGB(0, 0, 192), enabled = true)
    setDefaultsForSyntaxClass(TEMPLATE_VAR, new RGB(192, 0, 0), enabled = true)
    setDefaultsForSyntaxClass(TRAIT, new RGB(50, 147, 153), enabled = false)
    setDefaultsForSyntaxClass(OBJECT, new RGB(50, 147, 153), enabled = false)
    setDefaultsForSyntaxClass(PACKAGE, new RGB(0, 110, 4), enabled = false)
    setDefaultsForSyntaxClass(TYPE, new RGB(50, 147, 153), italic = true, enabled = false)
    setDefaultsForSyntaxClass(TYPE_PARAMETER, new RGB(23, 0, 129), underline = true, enabled = false)
  }  

  private def setDefaultsForSyntaxClass(
    syntaxClass: ScalaSyntaxClass,
    foregroundRGB: RGB,
    enabled: Boolean = true,
    backgroundRGBOpt: Option[RGB] = None,
    bold: Boolean = false,
    italic: Boolean = false,
    strikethrough: Boolean = false,
    underline: Boolean = false)(implicit scalaPrefStore: IPreferenceStore) =
    {
      lazy val WHITE = new RGB(255, 255, 255)
      scalaPrefStore.setDefault(syntaxClass.enabledKey, enabled)
      scalaPrefStore.setDefault(syntaxClass.foregroundColourKey, StringConverter.asString(foregroundRGB))
      val defaultBackgroundColour = StringConverter.asString(backgroundRGBOpt getOrElse WHITE)
      scalaPrefStore.setDefault(syntaxClass.backgroundColourKey, defaultBackgroundColour)
      scalaPrefStore.setDefault(syntaxClass.backgroundColourEnabledKey, backgroundRGBOpt.isDefined)
      scalaPrefStore.setDefault(syntaxClass.boldKey, bold)
      scalaPrefStore.setDefault(syntaxClass.italicKey, italic)
      scalaPrefStore.setDefault(syntaxClass.underlineKey, underline)
    }

}
