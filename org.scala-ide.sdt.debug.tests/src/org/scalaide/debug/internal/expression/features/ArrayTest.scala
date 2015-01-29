/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import scala.runtime.ScalaRunTime

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TestValues
import org.scalaide.debug.internal.expression.TestValues.ArraysTestCase
import org.scalaide.debug.internal.expression.proxies.ArrayJdiProxy
import org.scalaide.debug.internal.expression.UnsupportedFeature

class ArrayTest extends BaseIntegrationTest(ArrayTest) {

  import TestValues.any2String
  import ArraysTestCase._

  @Test
  def testNonLocalArrays(): Unit = {
    eval("Libs.intArray(1)", intArray(1), Java.boxed.Integer)

    eval("Libs.stringArray(1)", stringArray(1), Java.boxed.String)

    try {
      runCode("Libs.intArray(1) = 123")
      eval("Libs.intArray(1)", 123, Java.boxed.Integer)
    } finally {
      // rollback
      runCode(s"Libs.intArray(1) = ${intArray(1)}")
    }

    try {
      runCode("""Libs.stringArray(2) = "Ala ma kota w paski"""")
      eval("Libs.stringArray(2)", "Ala ma kota w paski", Java.boxed.String)
    } finally {
      // rollback
      runCode(s"""Libs.stringArray(2) = "${stringArray(2)}"""")
    }
  }

  @Test
  def testEmptyArray(): Unit =
    eval("emptyArray", ScalaRunTime.stringOf(emptyArray), Scala.Array(Scala.primitives.Int))

  /*
   * If this test does not fail anymore it means eclipse implementation of ArrayReference.getValues() is fixed
   * and workaround from Stringifier.show method could be removed.
   */
  @Test(expected = classOf[IndexOutOfBoundsException])
  def testIfEclipseStillDoesNotSupportEmptyArrays(): Unit = {
    val emptyArrayProxy @ ArrayJdiProxy(_, _) = runInEclipse("emptyArray")
    emptyArrayProxy.__underlying.getValues()
  }

  @Test
  def testIntArray(): Unit =
    eval("intArray", ScalaRunTime.stringOf(intArray), Scala.Array(Scala.primitives.Int))

  @Test
  def testStringArray(): Unit =
    eval("stringArray", ScalaRunTime.stringOf(stringArray), Scala.Array(Java.boxed.String))

  // TODO - O-5695 - add support for new array creation
  @Test(expected = classOf[UnsupportedFeature])
  def testIntArrayCreation(): Unit =
    eval("Array(1,2,3)", ScalaRunTime.stringOf(Array[Int](1, 2, 3)), Scala.Array(Scala.primitives.Int))

  @Test
  def testIntArrayCreationWorkaround(): Unit = eval(
    code = """val a = new Array[Int](3); a(0) = 1; a(1) = 2; a(2) = 3; a""",
    expectedValue = ScalaRunTime.stringOf(Array[Int](1, 2, 3)),
    expectedType = Scala.Array(Scala.primitives.Int))

  // TODO - O-5695 - add support for new array creation
  @Test(expected = classOf[UnsupportedFeature])
  def testStringArrayCreation(): Unit =
    eval("""Array("ala", "ola", "ula")""", ScalaRunTime.stringOf(Array("ala", "ola", "ula")), Scala.Array(Java.boxed.String))

  @Test
  def testStringArrayCreationWorkaround(): Unit = eval(
    code = """val a = new Array[String](3); a(0) = "ala"; a(1) = "ola"; a(2) = "ula"; a""",
    expectedValue = ScalaRunTime.stringOf(Array("ala", "ola", "ula")),
    expectedType = Scala.Array(Java.boxed.String))

  @Test
  def testIntArrayCreationWithNew(): Unit =
    eval("new Array[Int](2)", ScalaRunTime.stringOf(new Array[Int](2)), Scala.Array(Scala.primitives.Int))

  @Test
  def testStringArrayCreationWithNew(): Unit =
    eval("new Array[String](10)", ScalaRunTime.stringOf(new Array[String](10)), Scala.Array(Java.boxed.String))

  @Test
  def testIntArrayAccess(): Unit =
    eval("intArray(1)", intArray(1), Java.boxed.Integer)

  @Test
  def testStringArrayAccess(): Unit =
    eval("stringArray(1)", stringArray(1), Java.boxed.String)

  @Test
  def testIntArrayUpdate(): Unit = try {
    runCode("intArray(1) = 123")
    eval("intArray(1)", 123, Java.boxed.Integer)
  } finally {
    // rollback
    runCode(s"intArray(1) = ${intArray(1)}")
  }

  @Test
  def testStringArrayUpdate(): Unit = try {
    runCode("""stringArray(2) = "Ala ma kota w paski"""")
    eval("stringArray(2)", "Ala ma kota w paski", Java.boxed.String)
  } finally {
    // rollback
    runCode(s"""stringArray(2) = "${stringArray(2)}"""")
  }

  @Test
  def testIntArrayLength(): Unit =
    eval("intArray.length", intArray.length, Java.boxed.Integer)

  @Test
  def testStringArrayLength(): Unit =
    eval("stringArray.length", stringArray.length, Java.boxed.Integer)

  @Test
  def testMethodTakingIntArray(): Unit =
    eval(s"$arrayIdentity(intArray)", ScalaRunTime.stringOf(intArray), Scala.Array(Scala.primitives.Int))

  @Test
  def testMethodTakingStringArray(): Unit =
    eval(s"$arrayIdentity(stringArray)", ScalaRunTime.stringOf(stringArray), Scala.Array(Java.boxed.String))

  @Test
  def testMethodTakingNewIntArray(): Unit =
    eval(s"$arrayIdentity(new Array[Int](2))", ScalaRunTime.stringOf(new Array[Int](2)), Scala.Array(Scala.primitives.Int))

  @Test
  def testMethodTakingNewStringArray(): Unit =
    eval(
      code = s"$arrayIdentity(new Array[String](10))",
      expectedValue = ScalaRunTime.stringOf(new Array[String](10)),
      expectedType = Scala.Array(Java.boxed.String))

  @Ignore("TODO - O-5695 - add support for rich methods on arrays")
  @Test
  def testRichArrayMethods(): Unit = {
    eval("stringArray.head", stringArray.head, Java.boxed.String)
    eval("stringArray ++ intArray", stringArray ++ intArray, Scala.arrayType)
    eval("intArray.map { (i: Int) => i.toString }", intArray.map { (i: Int) => i.toString }, Scala.arrayType)
  }

  @Test
  def testNestedArrayAccess(): Unit =
    eval("nestedArray(0)", ScalaRunTime.stringOf(nestedArray(0)), Scala.Array(Scala.primitives.Int))

  @Test
  def testNestedArrayElementAccess(): Unit =
    eval("nestedArray(0)(2)", nestedArray(0)(2), Java.boxed.Integer)

  @Test
  def testNestedObjectArrayAccess(): Unit =
    eval("nestedObjectArray(0)", ScalaRunTime.stringOf(nestedObjectArray(0)), Scala.Array(Java.boxed.String))

  @Test
  def testNestedObjectArrayElementAccess(): Unit =
    eval("nestedObjectArray(0)(2)", nestedObjectArray(0)(2), Java.boxed.String)
}

object ArrayTest extends BaseIntegrationTestCompanion(ArraysTestCase)