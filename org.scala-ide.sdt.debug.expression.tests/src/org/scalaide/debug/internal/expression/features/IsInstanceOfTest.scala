/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TestValues.InstanceOfTestCase

class IsInstanceOfTest extends BaseIntegrationTest(IsInstanceOfTest) {

  import TestValues.InstanceOfTestCase._

  @Test
  def simpleIsInstanceOf(): Unit = {
    eval("string.isInstanceOf[String]", string.isInstanceOf[String], Java.boxed.Boolean)
    eval("string.isInstanceOf[java.util.Date]", string.isInstanceOf[java.util.Date], Java.boxed.Boolean)
  }

  @Test
  def primitiveIsInstanceOf(): Unit = {
    eval("byte.isInstanceOf[Byte]", byte.isInstanceOf[Byte], Java.boxed.Boolean)
    eval("short.isInstanceOf[Short]", short.isInstanceOf[Short], Java.boxed.Boolean)
    eval("int.isInstanceOf[Int]", int.isInstanceOf[Int], Java.boxed.Boolean)
    eval("long.isInstanceOf[Long]", long.isInstanceOf[Long], Java.boxed.Boolean)
    eval("float.isInstanceOf[Float]", float.isInstanceOf[Float], Java.boxed.Boolean)
    eval("double.isInstanceOf[Double]", double.isInstanceOf[Double], Java.boxed.Boolean)
    eval("boolean.isInstanceOf[Boolean]", boolean.isInstanceOf[Boolean], Java.boxed.Boolean)
  }

  @Test
  def negativePrimitiveIsInstanceOf(): Unit = {
    eval("int.isInstanceOf[Long]", int.isInstanceOf[Long], Java.boxed.Boolean)
    eval("int.isInstanceOf[Float]", int.isInstanceOf[Float], Java.boxed.Boolean)
    eval("float.isInstanceOf[Double]", float.isInstanceOf[Double], Java.boxed.Boolean)
  }

  @Test
  def primitiveArrayIsInstanceOf(): Unit = {
    eval("intArray.isInstanceOf[Array[Int]]", intArray.isInstanceOf[Array[Int]], Java.boxed.Boolean)
    eval("intArray.isInstanceOf[Array[Long]]", intArray.isInstanceOf[Array[Long]], Java.boxed.Boolean)
  }

  @Test
  def objectArrayIsInstanceOf(): Unit = {
    eval("objectArray.isInstanceOf[Array[String]]", objectArray.isInstanceOf[Array[String]], Java.boxed.Boolean)
    eval("objectArray.isInstanceOf[Array[java.util.Date]]", objectArray.isInstanceOf[Array[java.util.Date]], Java.boxed.Boolean)
  }

  @Ignore("TODO - add support for variance")
  @Test
  def arrayVariance(): Unit = {
    eval("fooArray.isInstanceOf[Array[Foo]]", fooArray.isInstanceOf[Array[Foo]], Java.boxed.Boolean)
    eval("barArray.isInstanceOf[Array[Foo]]", barArray.isInstanceOf[Array[Foo]], Java.boxed.Boolean)
    eval("fooArray.isInstanceOf[Array[Bar]]", fooArray.isInstanceOf[Array[Bar]], Java.boxed.Boolean)
    eval("barArray.isInstanceOf[Array[Bar]]", barArray.isInstanceOf[Array[Bar]], Java.boxed.Boolean)
  }

  @Test
  def lambdasAndIsInstanceOfIntegration(): Unit =
    eval("""List[Any](1, "2", 3).filter(_.isInstanceOf[java.lang.Integer])""", List(1, 3), Scala.::)

  @Test
  def genericIsInstanceOf(): Unit =
    eval("intList.isInstanceOf[List[Int]]", intList.isInstanceOf[List[Int]], Java.boxed.Boolean)

  @Test
  def erasureGenericIsInstanceOf(): Unit =
    eval("intList.isInstanceOf[List[String]]", intList.isInstanceOf[List[String]], Java.boxed.Boolean)

  @Test
  def wildcardGenericIsInstanceOf(): Unit =
    eval("intList.isInstanceOf[List[_]]", intList.isInstanceOf[List[_]], Java.boxed.Boolean)

  @Test
  def nullIsInstanceOf(): Unit =
    eval("nullVal.isInstanceOf[String]", nullVal.isInstanceOf[String], Java.boxed.Boolean)

  @Test
  def unitIsInstanceOf(): Unit = {
    eval("unit.isInstanceOf[Unit]", unit.isInstanceOf[Unit], Java.boxed.Boolean)
    eval("unit.isInstanceOf[String]", unit.isInstanceOf[String], Java.boxed.Boolean)
  }

  @Test
  def traitToClassToObjectInheritance(): Unit = {
    eval("A1.isInstanceOf[A1]", A1.isInstanceOf[A1], Java.boxed.Boolean)
    eval("A2.isInstanceOf[A1]", A2.isInstanceOf[A1], Java.boxed.Boolean)
    eval("A2.isInstanceOf[A2]", A2.isInstanceOf[A1], Java.boxed.Boolean)
    eval("A3.isInstanceOf[A1]", A3.isInstanceOf[A1], Java.boxed.Boolean)
    eval("A3.isInstanceOf[A2]", A3.isInstanceOf[A2], Java.boxed.Boolean)
    eval("A3.isInstanceOf[A3.type]", A3.isInstanceOf[A3.type], Java.boxed.Boolean)
  }

  @Test
  def traitToClassToClassInheritance(): Unit = {
    eval("B2.isInstanceOf[B1]", B2.isInstanceOf[B1], Java.boxed.Boolean)
    eval("B3.isInstanceOf[B1]", B3.isInstanceOf[B1], Java.boxed.Boolean)
    eval("B3.isInstanceOf[B2]", B3.isInstanceOf[B2], Java.boxed.Boolean)
  }

  @Test
  def traitToTraitToClassInheritance(): Unit = {
    eval("C2.isInstanceOf[C1]", C2.isInstanceOf[C1], Java.boxed.Boolean)
    eval("C3.isInstanceOf[C1]", C3.isInstanceOf[C1], Java.boxed.Boolean)
    eval("C3.isInstanceOf[C2]", C3.isInstanceOf[C2], Java.boxed.Boolean)
  }

  @Test
  def multipleTraitsToClassInheritance(): Unit = {
    eval("D3.isInstanceOf[D1]", D3.isInstanceOf[D1], Java.boxed.Boolean)
    eval("D3.isInstanceOf[D2]", D3.isInstanceOf[D2], Java.boxed.Boolean)
  }
}

object IsInstanceOfTest extends BaseIntegrationTestCompanion(InstanceOfTestCase)
