package org.scalaide.extensions

/**
 * Base interface for all Scala IDE extensions.
 */
trait ScalaIdeExtension

object ExtensionSetting {
  import reflect.runtime.universe._

  def fullyQualifiedName[A : TypeTag]: String =
    typeOf[A].typeSymbol.fullName

  def simpleName[A : TypeTag]: String =
    typeOf[A].typeSymbol.name.toString()
}

trait ExtensionSetting