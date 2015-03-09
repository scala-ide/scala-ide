package org.scalaide.debug

import org.scalaide.extensions.ScalaIdeExtension
import com.sun.jdi.event.Event

trait ScalaIdeDebugExtension {

}

/**
 * This extension allows clients to retrieve and handle debug events.
 */
trait DebugEventHandler extends ScalaIdeDebugExtension {

  /**
   * Every time a debug event occurs, it is sent to all registered debug event
   * handlers. In case an event handler can handle an event, it needs to return
   * a value, in case an event can't be handled, `None` needs to be returned. If
   * an error occurred in the event handler it may be useful to also return
   * `None` in order to allow a different event handler to handle the event.
   *
   * All event handler are executed one after another, which means that there
   * shouldn't exist two event handlers that can handle the same event -
   * otherwise the behavior of the IDE depends on the execution order of the
   * handlers.
   */
  def handleEvent(event: Event, context: DebugContext): Option[_]
}
