package scala.tools.nsc
package interactive.compat

import scala.reflect.NameTransformer
import scala.tools.nsc.Global 
import ch.epfl.lamp.fjbg.{ JArrayType, JMethodType, JObjectType, JType }

trait JVMUtilsSupport { self : Global =>

  protected lazy val jvmUtil = new genJVM.BytecodeUtil {}

  def javaFlags(sym : Symbol) : Int = genJVM.javaFlags(sym)
}
