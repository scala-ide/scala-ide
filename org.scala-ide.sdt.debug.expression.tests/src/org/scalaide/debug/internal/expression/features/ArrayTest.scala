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

  import ArraysTestCase._

  @Test
  def testNonLocalIntArray(): Unit =
    eval("Libs.intArray(1)", intArray(1), Java.primitives.int)

  @Test
  def testNonLocalStringArray(): Unit =
    eval("Libs.stringArray(1)", stringArray(1), Java.String)

  @Test
  def testNonLocalIntArrayAssignment(): Unit =
    testAssignment(on = "Libs.intArray(1)", tpe = Java.primitives.int, values = "123", "345")

  @Test
  def testNonLocalStringArrayAssignment(): Unit =
    testAssignment(on = "Libs.stringArray(2)", tpe = Java.String, values = s("Ala"), s("Ola"), s("Ula"))

  @Test
  def testEmptyArray(): Unit =
    eval("emptyArray", emptyArray, Scala.Array(Scala.primitives.Int))

  /*
   * If this test does not fail anymore it means eclipse implementation of ArrayReference.getValues() is fixed
   * and workaround from Stringifier.show method could be removed.
   */
  @Test(expected = classOf[IndexOutOfBoundsException])
  def testIfEclipseStillDoesNotSupportEmptyArrays(): Unit = {
    val emptyArrayProxy @ ArrayJdiProxy(_, _) = runInEclipse("emptyArray")
    emptyArrayProxy.__value.getValues()
  }

  @Test
  def testIntArray(): Unit =
    eval("intArray", intArray, Scala.Array(Scala.primitives.Int))

  @Test
  def testStringArray(): Unit =
    eval("stringArray", stringArray, Scala.Array(Java.String))

  @Test
  def testNestedIntArray(): Unit =
    eval("nestedArray", nestedArray, Scala.Array(Scala.Array(Scala.primitives.Int)))

  @Test
  def testNestedStringArray(): Unit =
    eval("nestedObjectArray", nestedObjectArray, Scala.Array(Scala.Array(Java.String)))

  @Test
  def testIntListToArray(): Unit =
    eval("List(1, 2, 3).toArray", Array(1, 2, 3), Scala.Array(Scala.primitives.Int))

  @Test
  def testStringListToArray(): Unit =
    eval("""List("a", "b").toArray""", Array("a", "b"), Scala.Array(Java.String))

  @Test
  def testIntArrayApply(): Unit =
    eval("Array(1,2,3)", Array[Int](1, 2, 3), Scala.Array(Scala.primitives.Int))

  @Test
  def testIntArrayCreationWorkaround(): Unit = eval(
    code = """val a = new Array[Int](3); a(0) = 1; a(1) = 2; a(2) = 3; a""",
    expectedValue = Array[Int](1, 2, 3),
    expectedType = Scala.Array(Scala.primitives.Int))

  @Test
  def testStringArrayApply(): Unit = eval(
    """Array("ala", "ola", "ula")""",
    Array("ala", "ola", "ula"),
    Scala.Array(Java.String))

  @Test
  def testStringArrayCreationWorkaround(): Unit = eval(
    code = """val a = new Array[String](3); a(0) = "ala"; a(1) = "ola"; a(2) = "ula"; a""",
    expectedValue = Array("ala", "ola", "ula"),
    expectedType = Scala.Array(Java.String))

  @Test
  def testArrayWithEmptyString(): Unit =
    eval("""Array("")""", Array(""), Scala.Array(Java.String))

  @Test
  def testNestedMixedAnyValArrayApply(): Unit = eval(
    "Array(Array(1,2,3), Array(2.0, 3.0, 4.0))",
    Array(Array(1, 2, 3), Array(2.0, 3.0, 4.0)),
    Scala.Array(Java.Object))

  @Test
  def testNestedMixedAnyArrayApply(): Unit = eval(
    "Array(Array(1,2,3), Array(\"foo\", \"bar\"))",
    Array(Array(1, 2, 3), Array("foo", "bar")),
    Scala.Array(Java.Object))

  @Test
  def testNestedIntArrayApply(): Unit = eval(
    "Array(Array(1,2,3))",
    Array(Array(1, 2, 3)),
    Scala.Array(Scala.Array(Scala.primitives.Int)))

  @Test
  def testNestedStringArrayApply(): Unit = eval(
    """Array(Array("1","2","3"))""",
    Array(Array("1", "2", "3")),
    Scala.Array(Scala.Array(Java.String)))

  @Test
  def testIntArrayCreationWithNew(): Unit =
    eval("new Array[Int](2)", new Array[Int](2), Scala.Array(Scala.primitives.Int))

  @Test
  def testStringArrayCreationWithNew(): Unit =
    eval("new Array[String](10)", new Array[String](10), Scala.Array(Java.String))

  @Test
  def testIntArrayAccess(): Unit =
    eval("intArray(1)", intArray(1), Java.primitives.int)

  @Test
  def testStringArrayAccess(): Unit =
    eval("stringArray(1)", stringArray(1), Java.String)

  @Test
  def testIntArrayUpdate(): Unit =
    testAssignment(on = "intArray(1)", tpe = Java.primitives.int, values = "123", "345")

  @Test
  def testStringArrayUpdate(): Unit =
    testAssignment(on = "stringArray(2)", tpe = Java.String, values = s("Ala"), s("Ola"), s("Ula"))

  @Test
  def testIntArrayLength(): Unit =
    eval("intArray.length", intArray.length, Java.primitives.int)

  @Test
  def testStringArrayLength(): Unit =
    eval("stringArray.length", stringArray.length, Java.primitives.int)

  @Test
  def testMethodTakingIntArray(): Unit =
    eval(s"$arrayIdentity(intArray)", intArray, Scala.Array(Scala.primitives.Int))

  @Test
  def testMethodTakingStringArray(): Unit =
    eval(s"$arrayIdentity(stringArray)", stringArray, Scala.Array(Java.String))

  @Test
  def testMethodTakingNewIntArray(): Unit = eval(
    s"$arrayIdentity(new Array[Int](2))",
    new Array[Int](2),
    Scala.Array(Scala.primitives.Int))

  @Test
  def testMethodTakingNewStringArray(): Unit = eval(
    code = s"$arrayIdentity(new Array[String](10))",
    expectedValue = new Array[String](10),
    expectedType = Scala.Array(Java.String))

  @Ignore("Potential bug in Toolbox.")
  @Test
  def testRichArrayMethodsHead(): Unit =
    eval("stringArray.head", stringArray.head, Java.String)

  @Ignore("Potential bug in Toolbox.")
  @Test
  def testRichArrayMethodsConcatenation(): Unit =
    eval("stringArray ++ intArray", stringArray ++ intArray, Scala.Array(Java.Object))

  @Ignore("Potential bug in Toolbox.")
  @Test
  def testRichArrayMethodsMap(): Unit =
    eval("intArray.map { _.toString }", ScalaRunTime.stringOf(intArray.map { _.toString }), Scala.Array(Java.String))

  @Test
  def testNestedArrayAccess(): Unit =
    eval("nestedArray(0)", nestedArray(0), Scala.Array(Scala.primitives.Int))

  @Test
  def testNestedArrayElementAccess(): Unit =
    eval("nestedArray(0)(2)", nestedArray(0)(2), Java.primitives.int)

  @Test
  def testNestedObjectArrayAccess(): Unit =
    eval("nestedObjectArray(0)", nestedObjectArray(0), Scala.Array(Java.String))

  @Test
  def testNestedObjectArrayElementAccess(): Unit =
    eval("nestedObjectArray(0)(2)", nestedObjectArray(0)(2), Java.String)

  // Tests for displaying arrays

  @Test
  def displayEmptyArray(): Unit =
    eval("Array[Int]()", "Array()", Scala.Array(Scala.primitives.Int))

  @Test
  def displayIntArray(): Unit =
    eval("Array(1, 2, 3)", Array(1, 2, 3), Scala.Array(Scala.primitives.Int))

  @Test
  def displayStringArray(): Unit =
    eval("""Array("1", "2", "3")""", Array("1", "2", "3"), Scala.Array(Java.String))

  @Test
  def displayNestedArray(): Unit = eval(
    "Array(Array(1, 2, 3), Array(1, 2, 3))",
    Array(Array(1, 2, 3), Array(1, 2, 3)),
    Scala.Array(Scala.Array(Scala.primitives.Int)))

  @Test
  def displayListWithArray(): Unit =
    eval("List(Array(1, 2, 3), Array(1.0, 2.0, 3.0))", List(Array(1, 2, 3), Array(1.0, 2.0, 3.0)), Scala.::)

  // this one sometimes (depending on test order) fails assertion in:
  // scala.tools.nsc.transform.AddInterfaces$LazyImplClassType.implType$1(AddInterfaces.scala:196)
  @Test
  def displayMapWithArray(): Unit =
    eval("Map(1 -> Array(1, 2, 3))", Map(1 -> Array(1, 2, 3)), "scala.collection.immutable.Map$Map1")

}

object ArrayTest extends BaseIntegrationTestCompanion(ArraysTestCase)
