/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression

import Names.Java
import Names.Scala

object TypeNameMappings {

  /**
   * Maps java primitives to Scala unified names.
   * Java arrays (int[], String[]) are mapped to Scala Arrays (Array[Int], Array[String]).
   * Other names are unchanged.
   */
  def javaNameToScalaName(typeName: String): String = typeName match {
    case Java.primitives.boolean => Scala.primitives.Boolean
    case Java.primitives.byte => Scala.primitives.Byte
    case Java.primitives.char => Scala.primitives.Char
    case Java.primitives.double => Scala.primitives.Double
    case Java.primitives.float => Scala.primitives.Float
    case Java.primitives.int => Scala.primitives.Int
    case Java.primitives.long => Scala.primitives.Long
    case Java.primitives.short => Scala.primitives.Short
    case Java.primitives.void => Scala.unitType
    case Java.primitives.Array(innerType) => Scala.Array(javaNameToScalaName(innerType))
    case other => other
  }
}