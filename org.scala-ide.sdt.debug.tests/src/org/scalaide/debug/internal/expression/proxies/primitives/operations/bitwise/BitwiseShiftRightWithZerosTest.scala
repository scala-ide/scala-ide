/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations.bitwise

import org.junit.Test
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues

class BitwiseShiftRightWithZerosTest extends BaseIntegrationTest(BitwiseShiftRightWithZerosTest) {

  import TestValues.Values._
  import TestValues.any2String

  @Test
  def `byte >>> sth`(): Unit = {
    eval("byte >>> byte2", byte >>> byte2, JavaBoxed.Integer)
    eval("byte >>> short2", byte >>> short2, JavaBoxed.Integer)
    eval("byte >>> char2", byte >>> char2, JavaBoxed.Integer)
    eval("byte >>> int2", byte >>> int2, JavaBoxed.Integer)
    eval("byte >>> long2", byte >>> long2, JavaBoxed.Integer)
    evalWithToolboxError("byte >>> float")
    evalWithToolboxError("byte >>> double")
  }

  @Test
  def `short >>> sth`(): Unit = {
    eval("short >>> byte2", short >>> byte2, JavaBoxed.Integer)
    eval("short >>> short2", short >>> short2, JavaBoxed.Integer)
    eval("short >>> char2", short >>> char2, JavaBoxed.Integer)
    eval("short >>> int2", short >>> int2, JavaBoxed.Integer)
    eval("short >>> long2", short >>> long2, JavaBoxed.Integer)
    evalWithToolboxError("short >>> float")
    evalWithToolboxError("short >>> double")
  }

  @Test
  def `char >>> sth`(): Unit = {
    eval("char >>> byte2", char >>> byte2, JavaBoxed.Integer)
    eval("char >>> short2", char >>> short2, JavaBoxed.Integer)
    eval("char >>> char2", char >>> char2, JavaBoxed.Integer)
    eval("char >>> int2", char >>> int2, JavaBoxed.Integer)
    eval("char >>> long2", char >>> long2, JavaBoxed.Integer)
    evalWithToolboxError("char >>> float")
    evalWithToolboxError("char >>> double")
  }

  @Test
  def `int >>> sth`(): Unit = {
    eval("int >>> byte2", int >>> byte2, JavaBoxed.Integer)
    eval("int >>> short2", int >>> short2, JavaBoxed.Integer)
    eval("int >>> char", int >>> char, JavaBoxed.Integer)
    eval("int >>> int2", int >>> int2, JavaBoxed.Integer)
    eval("int >>> long2", int >>> long2, JavaBoxed.Integer)
    evalWithToolboxError("int >>> float")
    evalWithToolboxError("int >>> double")
  }

  @Test
  def `long >>> sth`(): Unit = {
    eval("long >>> byte2", long >>> byte2, JavaBoxed.Long)
    eval("long >>> short2", long >>> short2, JavaBoxed.Long)
    eval("long >>> char", long >>> char, JavaBoxed.Long)
    eval("long >>> int2", long >>> int2, JavaBoxed.Long)
    eval("long >>> long2", long >>> long2, JavaBoxed.Long)
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
  def `'c' >>> 2L`(): Unit = eval("'c' >>> 2L", 'c' >>> 2L, JavaBoxed.Long)

  @Test
  def `1 >>> 2L`(): Unit = eval("1 >>> 2L", 1 >>> 2L, JavaBoxed.Long)
}

object BitwiseShiftRightWithZerosTest extends BaseIntegrationTestCompanion
