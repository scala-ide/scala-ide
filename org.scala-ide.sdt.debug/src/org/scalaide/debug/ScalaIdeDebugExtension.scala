package org.scalaide.debug

import com.sun.jdi.event.Event

trait ScalaIdeDebugExtension {

}

/**
 * This extension allows clients to retrieve and handle debug events.
 */
trait DebugEventHandler extends ScalaIdeDebugExtension {

  /**
   * Every time a debug event occurs, it is sent to all registered debug event
   * handlers. In case an event handler can't handle an event, it needs to
   * return [[NoCommand]], otherwise another [[JdiEventCommand]].
   *
   * All registered event handlers are executed. Therefore it is important that
   * their implementations don't conflict with each other.
   */
  def handleEvent(event: Event, context: DebugContext): JdiEventCommand
}

/**
 * A marker trait whose values need to be returned by
 * [[DebugEventHandler.handleEvent]].
 */
sealed trait JdiEventCommand

/**
 * Specifies that the JVM needs to be suspended after the event handler is run.
 */
case object SuspendExecution extends JdiEventCommand

/**
 * Specifies that the JVM does not to be suspended after the event handler is
 * run.
 */
case object ContinueExecution extends JdiEventCommand

/**
 * Specifies that an event handler didn't come up with any meaningful value
 * that could be handled by the debugger implementation.
 */
case object NoCommand extends JdiEventCommand
