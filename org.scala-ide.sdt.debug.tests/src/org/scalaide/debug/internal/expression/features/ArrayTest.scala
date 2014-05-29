/*
* Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
  * are made available under the terms of the Scala License which accompanies this distribution, and
* is available at http://www.scala-lang.org/node/146
*/
package org.scalaide.debug.internal.expression.features

import scala.runtime.ScalaRunTime

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.ScalaOther
import org.scalaide.debug.internal.expression.TestValues

class ArrayTest extends BaseIntegrationTest(ArrayTest) {

  import TestValues._
  import TestValues.Arrays._

  @Test
  def testIntArray(): Unit = eval("intArray", ScalaRunTime.stringOf(intArray), ScalaOther.arrayType)

  @Test
  def testStringArray(): Unit = eval("stringArray", ScalaRunTime.stringOf(stringArray), ScalaOther.arrayType)

  @Test
  def testIntArrayAccess(): Unit = eval("intArray(1)", intArray(1), JavaBoxed.Integer)

  @Test
  def testStringArrayAccess(): Unit = eval("stringArray(1)", stringArray(1), JavaBoxed.String)

  @Test
  def testIntArrayUpdate(): Unit = {
    runCode("intArray(1) = 123")
    eval("intArray(1)", 123, JavaBoxed.Integer)
    // rollback
    runCode(s"intArray(1) = ${intArray(1)}")
  }

  @Test
  def testStringArrayUpdate(): Unit = {
    runCode("""stringArray(2) = "Ala ma kota w paski"""")
    eval("stringArray(2)", "Ala ma kota w paski", JavaBoxed.String)
    // rollback
    runCode(s"""stringArray(2) = "${stringArray(2)}"""")
  }

  @Test
  def testIntArrayLength(): Unit = eval("intArray.length", intArray.length, JavaBoxed.Integer)

  @Test
  def testStringArrayLength(): Unit = eval("stringArray.length", stringArray.length, JavaBoxed.Integer)

  @Test
  def testMethodTakingIntArray(): Unit = eval(s"$arrayIdentity(intArray)", ScalaRunTime.stringOf(intArray), ScalaOther.arrayType)

  @Test
  def testMethodTakingStringArray(): Unit = eval(s"$arrayIdentity(stringArray)", ScalaRunTime.stringOf(stringArray), ScalaOther.arrayType)

  @Ignore("TODO - add support for rich methods on arrays")
  @Test
  def testRichArrayMethods(): Unit = {
    eval("stringArray.head", stringArray.head, JavaBoxed.String)
    eval("stringArray ++ intArray", stringArray ++ intArray, ScalaOther.arrayType)
    eval("intArray.map { (i: Int) => i.toString }", intArray.map { (i: Int) => i.toString }, ScalaOther.arrayType)
  }
}

object ArrayTest extends BaseIntegrationTestCompanion(
  fileName = TestValues.arraysFileName,
  lineNumber = TestValues.arraysLine)