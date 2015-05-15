package org.scalaide.debug.internal.command

import org.scalaide.debug.internal.BaseDebuggerActor

object ScalaStep {
  case object Step
  case object Stop
}

/** A step in the Scala debug model. Implementations need to be thread safe. */
trait ScalaStep {
  /** Initiate the step action. */
  def step(): Unit

  /** Terminates the step action and clean the resources. */
  def stop(): Unit
}

class ScalaStepImpl(companionActor: BaseDebuggerActor) extends ScalaStep {
  override def step(): Unit = companionActor ! ScalaStep.Step
  override def stop(): Unit = companionActor ! ScalaStep.Stop
}