package org.scalaide.extensions

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.eclipse.jface.preference.IPreferenceStore
import org.scalaide.core.ScalaPlugin
import org.scalaide.logging.HasLogger
import org.scalaide.util.internal.Commons

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
    ScalaPlugin.prefStore
}
