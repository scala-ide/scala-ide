/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test

import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala

class ObjectsTest extends BaseIntegrationTest(ObjectsTest) {

  @Test
  def testListApply(): Unit = eval("List(1,2)", "List(1, 2)", Scala.::)

  @Test
  def testListApplyWithMkString(): Unit = eval("List(1,2).mkString", "12", Java.boxed.String)

  @Test
  def testNestedObject(): Unit = eval("LibObject.LibNestedObject.id", "2", Java.boxed.Integer)

  @Test
  def testMoreNestedObject(): Unit = eval("LibObject.LibNestedObject.LibMoreNestedObject.id", "3", Java.boxed.Integer)

  @Ignore("TODO - O-5899 - add support for objects nested in classes")
  @Test
  def testNestedObjectWithVal(): Unit = eval("LibObject.nestedClass.LibMoreNestedObject.id", "4", Java.boxed.Integer)

}

object ObjectsTest extends BaseIntegrationTestCompanion
