package org.scalaide.debug.internal.command

/** A step in the Scala debug model. Implementations need to be thread safe. */
trait ScalaStep {
  /** Initiate the step action. */
  def step(): Unit

  /** Terminates the step action and clean the resources. */
  def stop(): Unit
}
