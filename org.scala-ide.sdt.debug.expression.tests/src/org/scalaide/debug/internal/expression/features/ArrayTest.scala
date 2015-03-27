/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package features

import scala.runtime.ScalaRunTime

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TestValues.ArraysTestCase
import org.scalaide.debug.internal.expression.proxies.ArrayJdiProxy

class ArrayTest extends BaseIntegrationTest(ArrayTest) with AssignmentTest {

  import TestValues.any2String
  import ArraysTestCase._

  @Test
  def testNonLocalIntArray(): Unit =
    eval("Libs.intArray(1)", intArray(1), Java.boxed.Integer)

  @Test
  def testNonLocalStringArray(): Unit =
    eval("Libs.stringArray(1)", stringArray(1), Java.boxed.String)

  @Test
  def testNonLocalIntArrayAssignment(): Unit =
    testAssignment(on = "Libs.intArray(1)", tpe = Java.boxed.Integer, values = "123", "345")

  @Test
  def testNonLocalStringArrayAssignment(): Unit =
    testAssignment(on = "Libs.stringArray(2)", tpe = Java.boxed.String, values = s("Ala"), s("Ola"), s("Ula"))

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

  @Test
  def testNestedIntArray(): Unit =
    eval("nestedArray", ScalaRunTime.stringOf(nestedArray), Scala.Array(Scala.Array(Scala.primitives.Int)))

  @Test
  def testNestedStringArray(): Unit =
    eval("nestedObjectArray", ScalaRunTime.stringOf(nestedObjectArray), Scala.Array(Scala.Array(Java.boxed.String)))

  @Test
  def testIntListToArray(): Unit =
    eval("List(1, 2, 3).toArray", "Array(1, 2, 3)", Scala.Array(Scala.primitives.Int))

  @Test
  def testStringListToArray(): Unit =
    eval("""List("a", "b").toArray""", """Array(a, b)""", Scala.Array(Java.boxed.String))

  @Test
  def testIntArrayApply(): Unit =
    eval("Array(1,2,3)", ScalaRunTime.stringOf(Array[Int](1, 2, 3)), Scala.Array(Scala.primitives.Int))

  @Test
  def testIntArrayCreationWorkaround(): Unit = eval(
    code = """val a = new Array[Int](3); a(0) = 1; a(1) = 2; a(2) = 3; a""",
    expectedValue = ScalaRunTime.stringOf(Array[Int](1, 2, 3)),
    expectedType = Scala.Array(Scala.primitives.Int))

  @Test
  def testStringArrayApply(): Unit = eval(
    """Array("ala", "ola", "ula")""",
    ScalaRunTime.stringOf(Array("ala", "ola", "ula")),
    Scala.Array(Java.boxed.String))

  @Test
  def testStringArrayCreationWorkaround(): Unit = eval(
    code = """val a = new Array[String](3); a(0) = "ala"; a(1) = "ola"; a(2) = "ula"; a""",
    expectedValue = ScalaRunTime.stringOf(Array("ala", "ola", "ula")),
    expectedType = Scala.Array(Java.boxed.String))

  @Test
  def testArrayWithEmptyString(): Unit =
    eval("""Array("")""", ScalaRunTime.stringOf(Array("")), Scala.Array(Java.boxed.String))

  @Test
  def testNestedMixedArrayApply(): Unit = eval(
    "Array(Array(1,2,3), Array(2.0, 3.0, 4.0))",
    ScalaRunTime.stringOf(Array(Array(1, 2, 3), Array(2.0, 3.0, 4.0))),
    Scala.Array(Java.Object))

  @Test
  def testNestedIntArrayApply(): Unit = eval(
    "Array(Array(1,2,3))",
    ScalaRunTime.stringOf(Array(Array(1, 2, 3))),
    Scala.Array(Scala.Array(Scala.primitives.Int)))

  @Test
  def testNestedStringArrayApply(): Unit = eval(
    """Array(Array("1","2","3"))""",
    ScalaRunTime.stringOf(Array(Array("1", "2", "3"))),
    Scala.Array(Scala.Array(Java.boxed.String)))

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
  def testIntArrayUpdate(): Unit =
    testAssignment(on = "intArray(1)", tpe = Java.boxed.Integer, values = "123", "345")

  @Test
  def testStringArrayUpdate(): Unit =
    testAssignment(on = "stringArray(2)", tpe = Java.boxed.String, values = s("Ala"), s("Ola"), s("Ula"))

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
  def testMethodTakingNewIntArray(): Unit = eval(
    s"$arrayIdentity(new Array[Int](2))",
    ScalaRunTime.stringOf(new Array[Int](2)),
    Scala.Array(Scala.primitives.Int))

  @Test
  def testMethodTakingNewStringArray(): Unit = eval(
    code = s"$arrayIdentity(new Array[String](10))",
    expectedValue = ScalaRunTime.stringOf(new Array[String](10)),
    expectedType = Scala.Array(Java.boxed.String))

  @Test
  def testRichArrayMethodsHead(): Unit =
    eval("stringArray.head", stringArray.head, Java.boxed.String)

  @Ignore("TODO - O-8564 Investigate test failures connected with HasNewBuilder and CanBuildFrom errors")
  @Test
  def testRichArrayMethodsConcatenation(): Unit =
    eval("stringArray ++ intArray", ScalaRunTime.stringOf(stringArray ++ intArray), Scala.Array(Java.Object))

  @Ignore("TODO - O-8564 Investigate test failures connected with HasNewBuilder and CanBuildFrom errors")
  @Test
  def testRichArrayMethodsMap(): Unit =
    eval("intArray.map { _.toString }", intArray.map { _.toString }, Scala.Array(Java.boxed.String))

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
