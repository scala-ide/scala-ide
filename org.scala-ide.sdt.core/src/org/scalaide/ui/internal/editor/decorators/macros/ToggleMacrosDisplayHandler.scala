package org.scalaide.ui.internal.editor.decorators.macros

import org.scalaide.ui.internal.actions.AbstractToggleHandler
import org.scalaide.ui.internal.preferences.MacrosPreferencePage

/**
 * Handler to toggle the Implicits Display (shortcut to avoid open Preferences,...)
 *
 * @see scala.tools.eclipse.ui.AbstractToggleHandler
 */

class ToggleMacrosDisplayHandler extends AbstractToggleHandler("org.scala-ide.sdt.core.commands.ToggleMacrosDisplay", MacrosPreferencePage.P_ACTIVE)