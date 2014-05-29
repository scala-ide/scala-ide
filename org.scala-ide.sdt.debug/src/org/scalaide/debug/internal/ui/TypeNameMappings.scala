/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.ui

import org.scalaide.debug.internal.expression.ScalaOther

object TypeNameMappings {
  lazy val javaTypesFromJdiToScalaTypes = Map(
    "boolean" -> classOf[Boolean].getSimpleName,
    "char" -> classOf[Char].getSimpleName,
    "byte" -> classOf[Byte].getSimpleName,
    "short" -> classOf[Short].getSimpleName,
    "int" -> classOf[Int].getSimpleName,
    "long" -> classOf[Long].getSimpleName,
    "float" -> classOf[Float].getSimpleName,
    "double" -> classOf[Double].getSimpleName,
    "void" -> classOf[Unit].getSimpleName)

  import ScalaOther.Array

  def javaNameToScalaName(typeName: String) = typeName match {
    case Array(innerType) => Array(javaTypesFromJdiToScalaTypes.getOrElse(innerType, innerType))
    case _ => javaTypesFromJdiToScalaTypes.getOrElse(typeName, typeName)
  }
}