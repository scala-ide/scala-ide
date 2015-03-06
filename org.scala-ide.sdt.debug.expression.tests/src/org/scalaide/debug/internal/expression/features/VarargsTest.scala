/*

 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.TestValues
import org.scalaide.debug.internal.expression.TestValues.VarargsTestCase

/**
 * Test uses a class with many overloaded methods containing both constant list of arguments and varargs
 */
class VarargsTest extends BaseIntegrationTest(VarargsTest) {

  import TestValues.any2String
  import VarargsTestCase._

  @Test
  def `fun()`(): Unit = eval("fun()", x, Java.boxed.Integer)

  @Test
  def `fun(Int)`(): Unit = eval("fun(i1)", i1 + x, Java.boxed.Integer)

  @Test
  def `fun(Long)`(): Unit = eval("fun(l1)", l1 + y, Java.boxed.Long)

  @Test
  def `fun(Int, Int)`(): Unit = eval("fun(i1, i2)", i1 + i2 + x * 3, Java.boxed.Integer)

  @Test
  def `fun(Long, Long)`(): Unit = eval("fun(l1, l2)", l1 + l2 + y * 3, Java.boxed.Long)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun(Int, Int, Int*) with 1`(): Unit = eval("fun(i1, i2, i3)", i1 + i2 + i3 + x * 4, Java.boxed.Integer)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun(Int, Int, Int*) with 2`(): Unit = eval("fun(i1, i2, i3, i4)", i1 + i2 + i3 + i4 + x * 4, Java.boxed.Integer)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun(Long*) with 4`(): Unit = eval("fun(i1, i2, i3, l4)", i1 + i2 + i3 + l4 + y * 2, Java.boxed.Long)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun(Long, Int, Int*) with 1`(): Unit = eval("fun(l1, i2, i3)", l1 + i2 + i3 + y * 4, Java.boxed.Long)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun(Long, Int, Int*) with 2`(): Unit = eval("fun(l1, i2, i3, i4)", l1 + i2 + i3 + i4 + y * 4, Java.boxed.Long)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun2(Int*) with 0`(): Unit = eval("fun2()", x * 5, Java.boxed.Integer)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun2(Int*) with 1`(): Unit = eval("fun2(i1)", i1 + x * 5, Java.boxed.Integer)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun2(Long, Int*) with 0`(): Unit = eval("fun2(l1)", l1 + y * 5, Java.boxed.Long)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun2(Int*) with 2`(): Unit = eval("fun2(i1, i2)", i1 + i2 + x * 5, Java.boxed.Integer)

  @Ignore("TODO - O-4581 - proper method with varargs should be chosen")
  @Test
  def `fun2(Long, Int*) with 1`(): Unit = eval("fun2(l1, i2)", l1 + i2 + y * 5, Java.boxed.Long)
}

object VarargsTest extends BaseIntegrationTestCompanion(VarargsTestCase)
