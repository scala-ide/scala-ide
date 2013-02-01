package scala.tools.eclipse.semantichighlighting

import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses

import org.eclipse.jface.preference.IPreferenceStore

trait Preferences {
  def isEnabled(): Boolean
  def isStrikethroughDeprecatedDecorationEnabled(): Boolean
  def isUseSyntacticHintsEnabled(): Boolean
  
  def store: IPreferenceStore
}

object Preferences {
  def apply(scalaStore: IPreferenceStore): Preferences =
    new DefaultSemanticHighlightingPreferences(scalaStore)

  private class DefaultSemanticHighlightingPreferences(override val store: IPreferenceStore) extends Preferences {
    override def isEnabled(): Boolean =
      store.getBoolean(ScalaSyntaxClasses.ENABLE_SEMANTIC_HIGHLIGHTING)

    override def isStrikethroughDeprecatedDecorationEnabled(): Boolean =
      store.getBoolean(ScalaSyntaxClasses.STRIKETHROUGH_DEPRECATED)

    override def isUseSyntacticHintsEnabled(): Boolean =
      store.getBoolean(ScalaSyntaxClasses.USE_SYNTACTIC_HINTS)
  }
}