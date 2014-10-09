package org.scalaide.core.internal.quickassist

import org.scalaide.core.quickassist.QuickAssist
import org.scalaide.util.eclipse.EclipseUtils

object QuickAssistMapping {

  final val QuickAssistId = "org.scala-ide.sdt.core.quickAssists"

  /**
   * Returns all existing quick assists mapped to the [[QuickAssistMapping]]
   * class. If an error occurred while trying to retrieve information for a
   * quick assist, it is filtered out and the error is logged.
   */
  def mappings: Seq[QuickAssistMapping] = {
    val elems = EclipseUtils.configElementsForExtension(QuickAssistId)
    elems flatMap { e =>
      EclipseUtils.withSafeRunner(s"Error occurred while trying to retrieve information from extension '$QuickAssistId'") {
        QuickAssistMapping(e.getAttribute("id"))(e.createExecutableExtension("class").asInstanceOf[QuickAssist])
      }
    }
  }
}

/**
 * A mapping for a quick assist extension that allows easy access to the defined
 * configuration. For documentation of the defined fields, see the quick assist
 * extension point.
 */
case class QuickAssistMapping(id: String)(unsafeInstanceAccess: QuickAssist) {

  /**
   * Gives access to the actual quick assist instance. Because these instances
   * can be defined by third party plugins they need to be executed in a safe
   * mode to protect the IDE against corruption.
   *
   * If an error occurs in `f` `None` is returned, otherwise the result of `f`.
   */
  def withInstance[A](f: QuickAssist => A): Option[A] =
    EclipseUtils.withSafeRunner(s"Error occurred while executing quick assist '$id'")(f(unsafeInstanceAccess))
}
