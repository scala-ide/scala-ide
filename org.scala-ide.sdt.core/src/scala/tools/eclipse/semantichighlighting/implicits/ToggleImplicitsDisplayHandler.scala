package scala.tools.eclipse.semantichighlighting.implicits

import scala.tools.eclipse.properties.ImplicitsPreferencePage
import scala.tools.eclipse.ui.AbstractToggleHandler

/**
 * Handler to toggle the Implicits Display (shortcut to avoid open Preferences,...)
 * 
 * @see scala.tools.eclipse.ui.AbstractToggleHandler
 */

class ToggleImplicitsDisplayHandler extends AbstractToggleHandler("org.scala-ide.sdt.core.commands.ToggleImplicitsDisplay", ImplicitsPreferencePage.P_ACTIVE)
