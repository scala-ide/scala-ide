
package scala.tools.eclipse.properties

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import scala.tools.eclipse.properties.ScalaSyntaxClasses._
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.swt.graphics.RGB
import org.eclipse.jface.resource.StringConverter

class ColourPreferenceInitializer extends AbstractPreferenceInitializer {

  def initializeDefaultPreferences() {
    val preferenceStore = ScalaPlugin.plugin.getPreferenceStore

    def initialize(syntaxClass: ScalaSyntaxClass, rgb: RGB, bold: Boolean = false, italic: Boolean = false,
      strikethrough: Boolean = false, underline: Boolean = false) =
      {
        val baseName = syntaxClass.baseName
//        PreferenceConverter.setDefault(preferenceStore, baseName + COLOUR_SUFFIX, rgb)
        // Removed PreferenceConverter, as it has the side-effect of creating a Display 
        // that breaks headless tests
        preferenceStore.setDefault(baseName + COLOUR_SUFFIX, StringConverter.asString(rgb))
        preferenceStore.setDefault(baseName + BOLD_SUFFIX, bold)
        preferenceStore.setDefault(baseName + ITALIC_SUFFIX, italic)
        preferenceStore.setDefault(baseName + STRIKETHROUGH_SUFFIX, strikethrough)
        preferenceStore.setDefault(baseName + UNDERLINE_SUFFIX, underline)
      }

    initialize(SINGLE_LINE_COMMENT, new RGB(63, 127, 95))
    initialize(MULTI_LINE_COMMENT, new RGB(63, 127, 95))
    initialize(SCALADOC, new RGB(63, 95, 191))
    initialize(KEYWORD, new RGB(127, 0, 85), bold = true)
    initialize(STRING, new RGB(42, 0, 255))
    initialize(MULTI_LINE_STRING, new RGB(42, 0, 255))
    initialize(DEFAULT, new RGB(0, 0, 0))
    initialize(OPERATOR, new RGB(0, 0, 0))
    initialize(BRACKET, new RGB(0, 0, 0))
    initialize(RETURN, new RGB(127, 0, 85), bold = true)
    initialize(BRACKET, new RGB(0, 0, 0))

    // See org.eclipse.wst.xml.ui.internal.preferences.XMLUIPreferenceInitializer
    initialize(XML_COMMENT, new RGB(63, 85, 191))
    initialize(XML_ATTRIBUTE_VALUE, new RGB(42, 0, 255), italic = true)
    initialize(XML_ATTRIBUTE_NAME, new RGB(127, 0, 127))
    initialize(XML_ATTRIBUTE_EQUALS, new RGB(0, 0, 0))
    initialize(XML_TAG_DELIMITER, new RGB(0, 128, 128))
    initialize(XML_TAG_NAME, new RGB(63, 127, 127))
    initialize(XML_PI, new RGB(0, 128, 128))
    initialize(XML_CDATA_BORDER, new RGB(0, 128, 128))

  }

}
