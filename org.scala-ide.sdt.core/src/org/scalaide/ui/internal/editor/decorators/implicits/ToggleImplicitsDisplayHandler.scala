package org.scalaide.ui.internal.editor.decorators.implicits

import org.scalaide.ui.internal.preferences.ImplicitsPreferencePage
import org.scalaide.ui.internal.actions.AbstractToggleHandler

/**
 * Handler to toggle the Implicits Display (shortcut to avoid open Preferences,...)
 */
class ToggleImplicitsDisplayHandler extends AbstractToggleHandler("org.scala-ide.sdt.core.commands.ToggleImplicitsDisplay", ImplicitsPreferencePage.P_ACTIVE)
