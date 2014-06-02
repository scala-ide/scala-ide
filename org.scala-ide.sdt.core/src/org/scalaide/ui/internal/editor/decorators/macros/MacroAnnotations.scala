package org.scalaide.ui.internal.editor.decorators.macros

import org.eclipse.jface.text.source.Annotation

object MacroNames{
  val macroExpansion = "macroExpansion"
  val macroExpandee = "macroExpandee"
  val enabledPreference = "scala.tools.eclipse.ui.preferences.macro.enabled"
}

object MacroExpansionAnnotation {
  final val ID = "org.scalaide.ui.editor.MacroExpansionAnnotation"
}

object Marker2Expand {
  final val ID = "org.scalaide.ui.editor.macro2expand"
}

object ScalaMacroMarker {
  final val ID = "org.scalaide.ui.editor.macroMarker"
}

class MacroExpansionAnnotation(text: String = "") extends Annotation(MacroExpansionAnnotation.ID, /*isPersistent*/ false, text)
