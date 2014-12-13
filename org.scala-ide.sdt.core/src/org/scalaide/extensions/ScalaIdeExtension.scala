package org.scalaide.extensions

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import org.eclipse.jface.preference.IPreferenceStore
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.Commons
import org.scalaide.core.IScalaPlugin

/**
 * Base interface for all Scala IDE extensions.
 */
trait ScalaIdeExtension {

  /**
   * The setting information is used to describe the behavior of the IDE
   * extension.
   *
   * Describing the behavior means that users may see information about this
   * extension in the "Scala" preference page of Eclipse.
   */
  def setting: ExtensionSetting
}

object ExtensionSetting {
  import reflect.runtime.universe._

  def fullyQualifiedName[A : TypeTag]: String =
    typeOf[A].typeSymbol.fullName

  def simpleName[A : TypeTag]: String =
    typeOf[A].typeSymbol.name.toString()

  /**
   * This method is meant to be used for multi line string literals that contain
   * the description for an [ExtensionSetting]. For readability the description
   * may occur of the following form:
   * {{{
   * """|line 1, \
   *    |line 1 extended
   *    |line 2, \
   *    |line 2 extended
   *    |"""
   * }}}
   * Here, we don't want to have the strip margin as part of the string.
   * Furthermore, we want to be able to express long lines without keeping them
   * as a single line in the sources. For this case one can append  a '\' at the
   * end of a line in the sources in order to get a single line in the resulting
   * string. In the above example the result of this method is:
   * {{{
   * line 1, line 1 extended
   * line 2, line 2 extended
   * }}}
   * It contains three lines, where the last line is empty.
   */
  def formatDescription(description: String): String = {
    val lineSeparator = """\\(\n|\r|\r\n)"""
    description.stripMargin.replaceAll(lineSeparator, "")
  }
}

trait ExtensionSetting extends HasLogger {

  /**
   * A uniqe ID that identifies the save action. A good value is the fully
   * qualified name of the save action class. This ID is only for internal
   * use in the IDE, users may never see it.
   */
  def id: String

  /**
   * The configuration values for the extension defined by [[id]]. If no
   * configuration is defined or the configuration is invalid, an empty `Map` is
   * returned.
   */
  def configuration: Map[String, String] = {
    def parse(str: String): Seq[(String, String)] = {
      if (str.isEmpty())
        Seq()
      else
        Commons.split(str, '\n').map { line ⇒
          Commons.split(line, '=') match {
            case Seq(k, v) ⇒ (k, v)
            case Seq(k) ⇒ (k, "true")
          }
        }
    }

    Try(parse(prefStore.getString(s"$id.config"))) match {
      case Success(seq) =>
        seq.toMap
      case Failure(f) =>
        eclipseLog.error(s"The configuration for '$id' couldn't be loaded.", f)
        Map()
    }
  }

  private[extensions] def prefStore: IPreferenceStore =
    IScalaPlugin().getPreferenceStore()
}
