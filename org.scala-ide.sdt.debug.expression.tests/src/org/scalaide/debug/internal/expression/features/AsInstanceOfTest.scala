/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package features

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TestValues.InstanceOfTestCase

class AsInstanceOfTest extends BaseIntegrationTest(AsInstanceOfTest) with DefaultBeforeAfterEach {

  import TestValues.InstanceOfTestCase._

  @Test
  def simpleAsInstanceOf(): Unit = {
    eval("check[Int](int.asInstanceOf[Int])", int, Java.primitives.int)
    eval("check[String](string.asInstanceOf[String])", string, Java.String)
  }

  @Test
  def arrayAsInstanceOf(): Unit = {
    eval("check[Array[Int]](intArray.asInstanceOf[Array[Int]])", intArray, Scala.Array(Scala.primitives.Int))
    eval("check[Array[String]](objectArray.asInstanceOf[Array[String]])", objectArray, Scala.Array(Java.String))
  }

  @Test
  def genericAsInstanceOf(): Unit = {
    eval("check[List[Int]](intList.asInstanceOf[List[Int]])", intList, Scala.::)
    eval("check[List[String]](stringList.asInstanceOf[List[String]])", stringList, Scala.::)
  }

}

object AsInstanceOfTest extends BaseIntegrationTestCompanion(InstanceOfTestCase) with DefaultBeforeAfterAll
