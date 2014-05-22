/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression

/**
 * Represents values from files in test workspaces which are used in tests.
 */
object TestValues {

  val argumentsFileName = "Arguments"
  val argumentsLine = 11

  val codeCompletionFileName = "CodeCompletion"
  val codeCompletionLineNumber = 26

  val exceptionsFileName = "Exceptions"
  val exceptionsLineNumber = 12

  val fileName = "Values"
  val breakpointLine = 39

  val implicitsFileName = "Implicits"
  val implicitsLineNumber = 15

  val nestedFileName = "Nested"
  val nestedScopeLine = 15

  val newInstancesFileName = "NewInstances"
  val newInstancesLineNumber = 8

  val thisFileName = "This"
  val thisLineNumber = 23

  val varargsFileName = "Varargs"
  val varargsLineNumber = 43

  val variablesFileName = "Variables"
  val variablesLineNumber = 17

  val visibilityFileName = "Visibility"
  val visibilityLineNumber = 23

  object Nested {
    val outerUsed = 1
    val outerUnused = 2
  }

  object This {
    val traitParam = 1
    val traitMethod = 1
    val classParam = 2
    val classMethod = 2
    val objectParam = 3
    val objectMethod = 3
  }

  object Values {
    val byte: Byte = 4
    val byte2: Byte = 3
    val short: Short = 6
    val short2: Short = 5
    val int = 1
    val int2 = 2
    val double = 1.1
    val double2 = 2.3
    val float = 1.1f
    val float2 = 0.7f
    val char = 'c'
    val char2 = 'd'
    val boolean = false
    val boolean2 = true
    val string = "Ala"
    val list = List(1, 2, 3)
    val * = 1
    val long = 1l
    val long2 = 2l
  }

  object Varargs {
    val x = 13
    val y = 17
    val i1 = 1
    val i2 = 2
    val i3 = 3
    val i4 = 4
    val l1 = 1L
    val l2 = 2L
    val l4 = 4L
  }

  import scala.language.implicitConversions
  implicit def any2String(x: Any) = x.toString()
}