package org.scalaide.ui.internal.wizards

import org.scalaide.logging.HasLogger
import org.scalaide.ui.wizards.FileCreator
import org.scalaide.util.eclipse.EclipseUtils

object FileCreatorMapping extends HasLogger {

  final val FileCreatorId = "org.scala-ide.sdt.core.fileCreator"

  /**
   * Returns all existing file creator extensions mapped to the
   * `FileCreatorMapping` class.
   */
  def mappings: Seq[FileCreatorMapping] = {
    val elems = EclipseUtils.configElementsForExtension(FileCreatorId)

    elems.filterNot(_.getAttribute("id") == "org.scalaide.ui.wizards.scalaCreator") flatMap { e =>
      EclipseUtils.withSafeRunner(s"Error while trying to retrieve information from extension '$FileCreatorId'") {
        FileCreatorMapping(
          e.getAttribute("id"),
          e.getAttribute("name"),
          e.getAttribute("templateId"),
          Option(e.getAttribute("icon")).getOrElse(""),
          e.getContributor().getName()
        )(e.createExecutableExtension("class").asInstanceOf[FileCreator])
      }
    }
  }
}

/**
 * A mapping for a file creator extension that allows easy access to the defined
 * configuration. For documentation of the defined fields, see the the file
 * creator extension point.
 */
case class FileCreatorMapping
  (id: String, name: String, templateId: String, iconPath: String, bundleId: String)
  (unsafeInstanceAccess: FileCreator) {

  /**
   * Gives access to the actual file creator instance. Because these instances
   * can be defined by third party plugins they need to be executed in a safe
   * mode to protect the IDE against corruption.
   *
   * If an error occurs in the passed function `None` is returned otherwise the
   * result of the function.
   */
  def withInstance[A](f: FileCreator => A): Option[A] = {
    EclipseUtils.withSafeRunner(s"An error occured while executing file creator '$name'.") {
      f(unsafeInstanceAccess)
    }
  }
}