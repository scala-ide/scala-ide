/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll

class ClassOfTest extends BaseIntegrationTest(ClassOfTest) {

  @Test
  def classOfString(): Unit =
    eval("classOf[String]", "class java.lang.String", "java.lang.Class")

  @Test
  def classOfPrimitive(): Unit = {
    eval("classOf[Boolean]", "boolean", "java.lang.Class")
    eval("classOf[Byte]", "byte", "java.lang.Class")
    eval("classOf[Char]", "char", "java.lang.Class")
    eval("classOf[Double]", "double", "java.lang.Class")
    eval("classOf[Float]", "float", "java.lang.Class")
    eval("classOf[Int]", "int", "java.lang.Class")
    eval("classOf[Long]", "long", "java.lang.Class")
    eval("classOf[Short]", "short", "java.lang.Class")
    eval("classOf[Unit]", "void", "java.lang.Class")
  }

  @Test
  def classOfList(): Unit =
    eval("classOf[List[Int]]", "class scala.collection.immutable.List", "java.lang.Class")

  @Test
  def classOfJavaList(): Unit =
    eval("classOf[java.util.ArrayList[Int]]", "class java.util.ArrayList", "java.lang.Class")

  @Test
  def classOfIntArray(): Unit =
    eval("classOf[scala.Array[Int]]", "class [I", "java.lang.Class")

  @Test
  def classOfStringArray(): Unit =
    eval("classOf[scala.Array[String]]", "class [Ljava.lang.String;", "java.lang.Class")

  @Ignore("TODO - O-8565 Add support for classOf[Array[Array[T]]]")
  @Test
  def classOfNestedIntArray(): Unit =
    eval("classOf[scala.Array[scala.Array[Int]]]", "class [[I", "java.lang.Class")

  @Ignore("TODO - O-8565 Add support for classOf[Array[Array[T]]]")
  @Test
  def classOfNestedStringArray(): Unit =
    eval("classOf[scala.Array[scala.Array[String]]]", "class [[Ljava.lang.String;", "java.lang.Class")
}

object ClassOfTest extends BaseIntegrationTestCompanion with DefaultBeforeAfterAll
