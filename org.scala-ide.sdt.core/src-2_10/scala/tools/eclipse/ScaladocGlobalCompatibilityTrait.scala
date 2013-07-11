package scala.tools.eclipse

import scala.tools.nsc.interactive.Global
import scala.tools.nsc.interactive.InteractiveReporter

/*
 * Trait used to keep 2.10 compatibility
 */

protected class ScaladocEnabledGlobal(settings:scala.tools.nsc.Settings, compilerReporter:InteractiveReporter, name:String) extends Global(settings, compilerReporter, name) {}

trait ScaladocGlobalCompatibilityTrait extends Global { outer =>

}
