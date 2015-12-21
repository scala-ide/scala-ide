package org.scalaide.debug.internal.extensions

import org.scalaide.util.eclipse.EclipseUtils
import org.scalaide.debug.DebugEventHandler

object EventHandlerMapping {

  final val EventHandlerId = "org.scala-ide.sdt.debug.eventHandler"

  /**
   * Returns all existing event handler extensions mapped to the
   * [[EventHandlerMapping]] class.
   */
  def mappings: Seq[EventHandlerMapping] = {
    val elems = EclipseUtils.configElementsForExtension(EventHandlerId)

    elems flatMap { e ⇒
      EclipseUtils.withSafeRunner(s"Error while trying to retrieve information from extension '$EventHandlerId'") {
        EventHandlerMapping(
          e.getAttribute("id"),
          e.getAttribute("name")
        )(e.createExecutableExtension("class").asInstanceOf[DebugEventHandler])
      }
    }
  }

}

/**
 * A mapping for an event handler that allows easy access to the defined
 * configuration. For documentation of the defined fields, see the event handler
 * extension point.
 */
case class EventHandlerMapping
  (id: String, name: String)
  (unsafeInstanceAccess: DebugEventHandler) {

  /**
   * Gives access to the actual event handler instance. Because these instances
   * can be defined by third party plugins, they need to be executed in a safe
   * mode to protect the IDE against corruption.
   *
   * If an error occurs in the passed function, `None` is returned, otherwise
   * the result of the function.
   */
  def withInstance[A](f: DebugEventHandler ⇒ A): Option[A] = {
    EclipseUtils.withSafeRunner(s"Error while executing debug event handler '$name'") {
      f(unsafeInstanceAccess)
    }
  }
}
