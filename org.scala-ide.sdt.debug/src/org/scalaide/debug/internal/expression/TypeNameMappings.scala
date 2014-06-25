/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression

object TypeNameMappings {

  /**
   * Maps java primitives to Scala unified names.
   * Java arrays (int[], String[]) are mapped to Scala Arrays (Array[Int], Array[String]).
   * Other names are unchanged.
   */
  def javaNameToScalaName(typeName: String): String = typeName match {
    case JavaPrimitives.boolean => ScalaPrimitivesUnified.Boolean
    case JavaPrimitives.byte => ScalaPrimitivesUnified.Byte
    case JavaPrimitives.char => ScalaPrimitivesUnified.Char
    case JavaPrimitives.double => ScalaPrimitivesUnified.Double
    case JavaPrimitives.float => ScalaPrimitivesUnified.Float
    case JavaPrimitives.int => ScalaPrimitivesUnified.Int
    case JavaPrimitives.long => ScalaPrimitivesUnified.Long
    case JavaPrimitives.short => ScalaPrimitivesUnified.Short
    case JavaPrimitives.void => ScalaOther.unitType
    case JavaPrimitives.Array(innerType) => ScalaOther.Array(javaNameToScalaName(innerType))
    case other => other
  }
}