package scala.tools.eclipse.debug.command

import scala.tools.eclipse.debug.BaseDebuggerActor

object ScalaStep {
  case object Step
  case object Stop
}

/** A step in the Scala debug model. Implementations need to be thread safe. */
trait ScalaStep {
  /** Initiate the step action. */
  def step()

  /** Terminates the step action and clean the resources. */
  def stop()
}

class ScalaStepImpl(companionActor: BaseDebuggerActor) extends ScalaStep {
  override def step(): Unit = companionActor ! ScalaStep.Step
  override def stop(): Unit = companionActor ! ScalaStep.Stop
}

abstract class ScalaStepActor extends BaseDebuggerActor {
  private var _dispose: Boolean = false
  
  protected final def dispose(): Unit = { 
    if(!_dispose) {
      _dispose = true
      onDispose()
    }
  }
  
  protected def onDispose(): Unit 
}