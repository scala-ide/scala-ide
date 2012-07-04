package scala.tools.eclipse.debug.command

object ScalaStep {
  case object Step
  case object Stop
}

trait ScalaStep {
  
  
  /**
   * Initiate the step action
   */
  def step()
  
  /**
   * Terminates the step action and clean the resources
   */
  def stop()

}