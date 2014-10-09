package org.scalaide.extensions

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

trait ExtensionSetting