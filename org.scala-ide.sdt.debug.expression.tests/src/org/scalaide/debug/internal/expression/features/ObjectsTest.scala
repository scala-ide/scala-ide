/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala

class ObjectsTest extends BaseIntegrationTest(ObjectsTest) {

  @Test
  def testListApply(): Unit = eval("List(1,2)", List(1, 2), Scala.::)

  @Test
  def testListApplyWithMkString(): Unit = eval("List(1,2).mkString", 12, Java.String)

  @Test
  def testNestedObject(): Unit = eval("LibObject.LibNestedObject.id", 2, Java.primitives.int)

  @Test
  def testMoreNestedObject(): Unit = eval("LibObject.LibNestedObject.LibMoreNestedObject.id", 3, Java.primitives.int)

  @Test
  def testNestedObjectWithVal(): Unit = eval("LibObject.nestedClass.LibMoreNestedObject.id", 4, Java.primitives.int)

}

object ObjectsTest extends BaseIntegrationTestCompanion with DefaultBeforeAfterAll
