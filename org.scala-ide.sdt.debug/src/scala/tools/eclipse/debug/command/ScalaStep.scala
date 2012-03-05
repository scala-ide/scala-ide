package scala.tools.eclipse.debug.command

trait ScalaStep {
  
  /**
   * Initiate the step action
   */
  def step()
  
  /**
   * Terminates the step action
   */
  def stop()

}