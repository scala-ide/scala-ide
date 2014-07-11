package org.scalaide.ui.internal.wizards

import org.eclipse.core.runtime.CoreException
import org.scalaide.logging.HasLogger
import org.scalaide.ui.wizards.FileCreator
import org.scalaide.util.internal.eclipse.EclipseUtils

object FileCreatorMapping extends HasLogger {

  final val FileCreatorId = "org.scala-ide.sdt.core.fileCreator"

  /**
   * Returns all existing file creator extensions mapped to the
   * `FileCreatorMapping` class.
   */
  def mappings: Seq[FileCreatorMapping] = {
    val elems = EclipseUtils.configElementsForExtension(FileCreatorId)

    try
      elems.filterNot(_.getAttribute("id") == "org.scalaide.ui.wizards.scalaCreator").map(e => FileCreatorMapping(
        e.getAttribute("id"),
        e.getAttribute("name"),
        e.getAttribute("templateId"),
        Option(e.getAttribute("icon")).getOrElse(""),
        e.getContributor().getName()
      )(e.createExecutableExtension("class").asInstanceOf[FileCreator]))
    catch {
      case e: CoreException =>
        eclipseLog.error(s"Error while trying to retrieve information from extension '$FileCreatorId'", e)
        Seq()
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
    var a = null.asInstanceOf[A]
    EclipseUtils.withSafeRunner {
      a = f(unsafeInstanceAccess)
    }
    Option(a)
  }
}