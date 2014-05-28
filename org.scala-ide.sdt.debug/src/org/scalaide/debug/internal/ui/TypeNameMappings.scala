/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.ui

object TypeNameMappings {
  lazy val javaTypesFromJdiToScalaTypes = Map(
    "boolean" -> "Boolean",
    "char" -> "Char",
    "byte" -> "Byte",
    "short" -> "Short",
    "int" -> "Int",
    "long" -> "Long",
    "float" -> "Float",
    "double" -> "Double",
    "void" -> "Unit")

  def javaNameToScalaName(typeName: String) =
    if (typeName.endsWith("[]")) {
      val nonarrayType = typeName.substring(0, typeName.length() - 2)
      s"Array[${javaTypesFromJdiToScalaTypes.getOrElse(nonarrayType, nonarrayType)}]"
    } else javaTypesFromJdiToScalaTypes.getOrElse(typeName, typeName)
}