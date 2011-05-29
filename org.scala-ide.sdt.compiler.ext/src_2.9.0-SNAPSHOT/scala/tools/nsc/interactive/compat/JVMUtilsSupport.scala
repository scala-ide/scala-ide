package scala.tools.nsc
package interactive.compat

import scala.tools.nsc.Global 

trait JVMUtilsSupport { self : Global =>

  protected lazy val jvmUtil = new genJVM.BytecodeUtil {}

  def javaFlags(sym : Symbol) : Int = genJVM.javaFlags(sym)
}
