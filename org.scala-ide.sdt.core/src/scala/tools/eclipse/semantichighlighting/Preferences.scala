package scala.tools.eclipse.semantichighlighting

import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.TextAttribute

class Preferences(val store: IPreferenceStore) {
  def isEnabled(): Boolean =
    store.getBoolean(ScalaSyntaxClasses.ENABLE_SEMANTIC_HIGHLIGHTING)

  def isStrikethroughDeprecatedDecorationEnabled(): Boolean =
    store.getBoolean(ScalaSyntaxClasses.STRIKETHROUGH_DEPRECATED)

  def isUseSyntacticHintsEnabled(): Boolean =
    store.getBoolean(ScalaSyntaxClasses.USE_SYNTACTIC_HINTS)
    
  def isInterpolatedStringVariableDecorationEnabled(): Boolean =
    ScalaSyntaxClasses.VARIABLE_IN_INTERPOLATED_STRING.getStyleInfo(store).enabled
    
  def interpolatedStringTextAttribute(): TextAttribute =
    ScalaSyntaxClasses.VARIABLE_IN_INTERPOLATED_STRING.getTextAttribute(store)
}

object Preferences {
  def apply(store: IPreferenceStore): Preferences = new Preferences(store)
}