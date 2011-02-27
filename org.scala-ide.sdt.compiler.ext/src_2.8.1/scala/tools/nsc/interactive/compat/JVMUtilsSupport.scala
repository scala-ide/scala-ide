package scala.tools.nsc
package interactive.compat

import scala.reflect.NameTransformer
import scala.tools.nsc.Global 
import ch.epfl.lamp.fjbg.{ JArrayType, JMethodType, JObjectType, JType }

trait JVMUtilsSupport { self : Global =>

  protected lazy val jvmUtil =
    //BACK-2.8
    // new genJVM.BytecodeUtil {}
    currentRun.phaseNamed(genJVM.phaseName).asInstanceOf[genJVM.JvmPhase].codeGenerator

  def javaFlags(sym : Symbol) : Int = //BACK-2.8 genJVM.javaFlags(sym)
    jvmUtil.javaFlags(sym)
}
