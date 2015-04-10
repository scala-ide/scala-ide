/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations.bitwise

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues

class BitwiseShiftRightWithZerosTest extends BaseIntegrationTest(BitwiseShiftRightWithZerosTest) {

  import TestValues.ValuesTestCase._
  import TestValues.any2String

  @Test
  def `byte >>> sth`(): Unit = {
    eval("byte >>> byte2", byte >>> byte2, Java.boxed.Integer)
    eval("byte >>> short2", byte >>> short2, Java.boxed.Integer)
    eval("byte >>> char2", byte >>> char2, Java.boxed.Integer)
    eval("byte >>> int2", byte >>> int2, Java.boxed.Integer)
    eval("byte >>> long2", byte >>> long2, Java.boxed.Integer)
    evalWithToolboxError("byte >>> float")
    evalWithToolboxError("byte >>> double")
  }

  @Test
  def `short >>> sth`(): Unit = {
    eval("short >>> byte2", short >>> byte2, Java.boxed.Integer)
    eval("short >>> short2", short >>> short2, Java.boxed.Integer)
    eval("short >>> char2", short >>> char2, Java.boxed.Integer)
    eval("short >>> int2", short >>> int2, Java.boxed.Integer)
    eval("short >>> long2", short >>> long2, Java.boxed.Integer)
    evalWithToolboxError("short >>> float")
    evalWithToolboxError("short >>> double")
  }

  @Test
  def `char >>> sth`(): Unit = {
    eval("char >>> byte2", char >>> byte2, Java.boxed.Integer)
    eval("char >>> short2", char >>> short2, Java.boxed.Integer)
    eval("char >>> char2", char >>> char2, Java.boxed.Integer)
    eval("char >>> int2", char >>> int2, Java.boxed.Integer)
    eval("char >>> long2", char >>> long2, Java.boxed.Integer)
    evalWithToolboxError("char >>> float")
    evalWithToolboxError("char >>> double")
  }

  @Test
  def `int >>> sth`(): Unit = {
    eval("int >>> byte2", int >>> byte2, Java.boxed.Integer)
    eval("int >>> short2", int >>> short2, Java.boxed.Integer)
    eval("int >>> char", int >>> char, Java.boxed.Integer)
    eval("int >>> int2", int >>> int2, Java.boxed.Integer)
    eval("int >>> long2", int >>> long2, Java.boxed.Integer)
    evalWithToolboxError("int >>> float")
    evalWithToolboxError("int >>> double")
  }

  @Test
  def `long >>> sth`(): Unit = {
    eval("long >>> byte2", long >>> byte2, Java.boxed.Long)
    eval("long >>> short2", long >>> short2, Java.boxed.Long)
    eval("long >>> char", long >>> char, Java.boxed.Long)
    eval("long >>> int2", long >>> int2, Java.boxed.Long)
    eval("long >>> long2", long >>> long2, Java.boxed.Long)
    evalWithToolboxError("long >>> float")
    evalWithToolboxError("long >>> double")
  }

  @Test
  def `float >>> sth`(): Unit = {
    evalWithToolboxError("float >>> byte2")
    evalWithToolboxError("float >>> short2")
    evalWithToolboxError("float >>> char")
    evalWithToolboxError("float >>> int2")
    evalWithToolboxError("float >>> long2")
    evalWithToolboxError("float >>> float2")
    evalWithToolboxError("float >>> double")
  }

  @Test
  def `double >>> sth`(): Unit = {
    evalWithToolboxError("double >>> byte2")
    evalWithToolboxError("double >>> short2")
    evalWithToolboxError("double >>> char")
    evalWithToolboxError("double >>> int2")
    evalWithToolboxError("double >>> long2")
    evalWithToolboxError("double >>> float")
    evalWithToolboxError("double >>> double2")
  }

  // these result types below are Scala bug - not ours (see SI-8462)
  @Test
  def `'c' >>> 2L`(): Unit = eval("'c' >>> 2L", 'c' >>> 2L, Java.boxed.Long)

  @Test
  def `1 >>> 2L`(): Unit = eval("1 >>> 2L", 1 >>> 2L, Java.boxed.Long)
}

object BitwiseShiftRightWithZerosTest extends BaseIntegrationTestCompanion
