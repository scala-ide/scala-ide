package scala.tools.nsc
package interactive.compat

import scala.tools.nsc.Global 

trait JVMUtilsSupport { self : Global =>

  protected lazy val jvmUtil =
    //BACK-2.8
    // new genJVM.BytecodeUtil {}
    currentRun.phaseNamed(genJVM.phaseName).asInstanceOf[genJVM.JvmPhase].codeGenerator

  def javaFlags(sym : Symbol) : Int = //BACK-2.8 genJVM.javaFlags(sym)
    jvmUtil.javaFlags(sym)
}
