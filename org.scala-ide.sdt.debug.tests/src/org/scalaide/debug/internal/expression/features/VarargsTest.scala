/*

 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.TestValues

/**
 * Test uses a class with many overloaded methods containing both constant list of arguments and varargs
 */
class VarargsTest extends BaseIntegrationTest(VarargsTest) {

  import TestValues.Varargs._
  import TestValues.any2String

  @Test
  def `fun()`(): Unit = eval("fun()", x, JavaBoxed.Integer)

  @Test
  def `fun(Int)`(): Unit = eval("fun(i1)", i1 + x, JavaBoxed.Integer)

  @Test
  def `fun(Long)`(): Unit = eval("fun(l1)", l1 + y, JavaBoxed.Long)

  @Test
  def `fun(Int, Int)`(): Unit = eval("fun(i1, i2)", i1 + i2 + x * 3, JavaBoxed.Integer)

  @Test
  def `fun(Long, Long)`(): Unit = eval("fun(l1, l2)", l1 + l2 + y * 3, JavaBoxed.Long)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun(Int, Int, Int*) with 1`(): Unit = eval("fun(i1, i2, i3)", i1 + i2 + i3 + x * 4, JavaBoxed.Integer)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun(Int, Int, Int*) with 2`(): Unit = eval("fun(i1, i2, i3, i4)", i1 + i2 + i3 + i4 + x * 4, JavaBoxed.Integer)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun(Long*) with 4`(): Unit = eval("fun(i1, i2, i3, l4)", i1 + i2 + i3 + l4 + y * 2, JavaBoxed.Long)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun(Long, Int, Int*) with 1`(): Unit = eval("fun(l1, i2, i3)", l1 + i2 + i3 + y * 4, JavaBoxed.Long)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun(Long, Int, Int*) with 2`(): Unit = eval("fun(l1, i2, i3, i4)", l1 + i2 + i3 + i4 + y * 4, JavaBoxed.Long)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun2(Int*) with 0`(): Unit = eval("fun2()", x * 5, JavaBoxed.Integer)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun2(Int*) with 1`(): Unit = eval("fun2(i1)", i1 + x * 5, JavaBoxed.Integer)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun2(Long, Int*) with 0`(): Unit = eval("fun2(l1)", l1 + y * 5, JavaBoxed.Long)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun2(Int*) with 2`(): Unit = eval("fun2(i1, i2)", i1 + i2 + x * 5, JavaBoxed.Integer)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun2(Long, Int*) with 1`(): Unit = eval("fun2(l1, i2)", l1 + i2 + y * 5, JavaBoxed.Long)
}

object VarargsTest extends BaseIntegrationTestCompanion(
  fileName = TestValues.varargsFileName,
  lineNumber = TestValues.varargsLineNumber)
