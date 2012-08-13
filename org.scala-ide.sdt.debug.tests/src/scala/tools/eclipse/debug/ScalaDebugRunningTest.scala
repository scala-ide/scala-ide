package scala.tools.eclipse.debug

import scala.tools.eclipse.testsetup.TestProjectSetup

trait ScalaDebugRunningTest {

  val TYPENAME_FC_LS = "stepping.ForComprehensionListString"
  val TYPENAME_FC_LS2 = "stepping.ForComprehensionListString2"
  val TYPENAME_FC_LO = "stepping.ForComprehensionListObject"
  val TYPENAME_FC_LI = "stepping.ForComprehensionListInt"
  val TYPENAME_FC_LIO = "stepping.ForComprehensionListIntOptimized"
  val TYPENAME_AF_LI = "stepping.AnonFunOnListInt"
  val TYPENAME_AF_LS = "stepping.AnonFunOnListString"
  val TYPENAME_VARIABLES = "debug.Variables"
  val TYPENAME_SIMPLE_STEPPING = "stepping.SimpleStepping"

}