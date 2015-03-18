/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package features

import scala.runtime.ScalaRunTime

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TestValues.InstanceOfTestCase

class AsInstanceOfTest extends BaseIntegrationTest(AsInstanceOfTest) {

  import TestValues.InstanceOfTestCase._
  import TestValues.any2String

  @Test
  def simpleAsInstanceOf(): Unit = {
    eval("check[Int](int.asInstanceOf[Int])", int, Java.boxed.Integer)
    eval("check[String](string.asInstanceOf[String])", string, Java.boxed.String)
  }

  @Test
  def arrayAsInstanceOf(): Unit = {
    eval("check[Array[Int]](intArray.asInstanceOf[Array[Int]])", ScalaRunTime.stringOf(intArray), Scala.Array(Scala.primitives.Int))
    eval("check[Array[String]](objectArray.asInstanceOf[Array[String]])", ScalaRunTime.stringOf(objectArray), Scala.Array(Java.boxed.String))
  }

  @Test
  def genericAsInstanceOf(): Unit = {
    eval("check[List[Int]](intList.asInstanceOf[List[Int]])", intList, Scala.::)
    eval("check[List[String]](stringList.asInstanceOf[List[String]])", stringList, Scala.::)
  }

}

object AsInstanceOfTest extends BaseIntegrationTestCompanion(InstanceOfTestCase)
