package org.scalaide.core.internal.builder

import org.scalaide.util.eclipse.EclipseUtils

object BuildManagerFactoryMapping {

  final val BuildManagerFactoryId = "org.scala-ide.sdt.core.buildManagerFactory"

  /**
   * Returns all existing build manager factory extensions mapped to the
   * [[BuildManagerFactory]] class.
   */
  def mappings: Seq[BuildManagerFactoryMapping] = {
    val elems = EclipseUtils.configElementsForExtension(BuildManagerFactoryId)

    elems flatMap { e ⇒
      EclipseUtils.withSafeRunner(s"Error while trying to retrieve information from extension 'BuildManagerFactoryId'") {
        BuildManagerFactoryMapping(
          e.getAttribute("name"))(e.createExecutableExtension("class").asInstanceOf[BuildManagerFactory])
      }
    }
  }

}

/**
 * A mapping for an build manager factory that allows easy access to the defined
 * configuration.
 */
case class BuildManagerFactoryMapping(name: String)(unsafeInstanceAccess: BuildManagerFactory) {

  /**
   * Gives access to the actual build manager factory instance. Because these instances
   * can be defined by third party plugins, they need to be executed in a safe
   * mode to protect the IDE against corruption.
   *
   * If an error occurs in the passed function, `None` is returned, otherwise
   * the result of the function.
   */
  def withInstance[A](f: BuildManagerFactory ⇒ A): Option[A] = {
    EclipseUtils.withSafeRunner(s"Error while executing build manager factory '$name'") {
      f(unsafeInstanceAccess)
    }
  }
}
