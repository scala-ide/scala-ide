/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion

class ClassOfTest extends BaseIntegrationTest(ClassOfTest) {

  @Test
  def classOfString(): Unit =
    eval("classOf[String]", "class java.lang.String", "java.lang.Class")

  @Test
  def classOfInt(): Unit =
    eval("classOf[Int]", "class scala.Int", "java.lang.Class")

  @Test
  def classOfList(): Unit =
    eval("classOf[List[Int]]", "class scala.collection.immutable.List", "java.lang.Class")

  @Test
  def classOfJavaList(): Unit =
    eval("classOf[java.util.ArrayList[Int]]", "class java.util.ArrayList", "java.lang.Class")

  @Ignore("TODO - O-8565 Add support for classOf[Array[T]]")
  @Test
  def classOfIntArray(): Unit =
    eval("classOf[scala.Array[Int]]", "class scala.Array", "java.lang.Class")

  @Ignore("TODO - O-8565 Add support for classOf[Array[T]]")
  @Test
  def classOfStringArray(): Unit =
    eval("classOf[scala.Array[String]]", "class scala.Array", "java.lang.Class")
}

object ClassOfTest extends BaseIntegrationTestCompanion
