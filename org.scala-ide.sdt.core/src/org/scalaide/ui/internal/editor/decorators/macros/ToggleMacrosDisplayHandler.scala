package org.scalaide.ui.internal.editor.decorators.macros

import org.scalaide.ui.internal.preferences.MacrosPreferencePage
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.ui.PlatformUI
import org.scalaide.ui.internal.editor.macros.ScalaMacroEditor
import org.scalaide.ui.internal.actions.AbstractToggleHandler
import org.scalaide.core.internal.ScalaPlugin

/**
 * Handler to toggle the Macro Display (shortcut to avoid open Preferences,...)
 *
 * @see scala.tools.eclipse.ui.AbstractToggleHandler
 */

class ToggleMacrosDisplayHandler extends AbstractToggleHandler("org.scala-ide.sdt.core.commands.ToggleMacrosDisplay", MacrosPreferencePage.P_ACTIVE){
  def getPreference = ScalaPlugin().getPreferenceStore.getBoolean(MacrosPreferencePage.P_ACTIVE)
  override def execute(event: ExecutionEvent): Object = { //If unset => collapse all macros in all editors
    super.execute(event)
    if(!getPreference) {
      val editors = PlatformUI.getWorkbench.getActiveWorkbenchWindow.getActivePage.getEditorReferences.map(_.getEditor(false))
      editors.foreach(_.asInstanceOf[ScalaMacroEditor].collapseMacros())
    }
    null
  }
}