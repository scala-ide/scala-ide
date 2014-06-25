/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations.numeric

import org.junit.Test
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues
class SubtractionTest extends BaseIntegrationTest(SubtractionTest) {

  import TestValues.Values._
  import TestValues.any2String

  @Test
  def `byte - sth`(): Unit = {
    eval("byte - byte2", byte - byte2, JavaBoxed.Integer)
    eval("byte - short2", byte - short2, JavaBoxed.Integer)
    eval("byte - char2", byte - char2, JavaBoxed.Integer)
    eval("byte - int2", byte - int2, JavaBoxed.Integer)
    eval("byte - long2", byte - long2, JavaBoxed.Long)
    eval("byte - float", byte - float, JavaBoxed.Float)
    eval("byte - double", byte - double, JavaBoxed.Double)
  }

  @Test
  def `short - sth`(): Unit = {
    eval("short - byte2", short - byte2, JavaBoxed.Integer)
    eval("short - short2", short - short2, JavaBoxed.Integer)
    eval("short - char2", short - char2, JavaBoxed.Integer)
    eval("short - int2", short - int2, JavaBoxed.Integer)
    eval("short - long2", short - long2, JavaBoxed.Long)
    eval("short - float", short - float, JavaBoxed.Float)
    eval("short - double", short - double, JavaBoxed.Double)
  }

  @Test
  def `char - sth`(): Unit = {
    eval("char - byte2", char - byte2, JavaBoxed.Integer)
    eval("char - short2", char - short2, JavaBoxed.Integer)
    eval("char - char2", char - char2, JavaBoxed.Integer)
    eval("char - int2", char - int2, JavaBoxed.Integer)
    eval("char - long2", char - long2, JavaBoxed.Long)
    eval("char - float", char - float, JavaBoxed.Float)
    eval("char - double", char - double, JavaBoxed.Double)
  }

  @Test
  def `int - sth`(): Unit = {
    eval("int - byte2", int - byte2, JavaBoxed.Integer)
    eval("int - short2", int - short2, JavaBoxed.Integer)
    eval("int - char", int - char, JavaBoxed.Integer)
    eval("int - int2", int - int2, JavaBoxed.Integer)
    eval("int - long2", int - long2, JavaBoxed.Long)
    eval("int - float", int - float, JavaBoxed.Float)
    eval("int - double", int - double, JavaBoxed.Double)
  }

  @Test
  def `long - sth`(): Unit = {
    eval("long - byte2", long - byte2, JavaBoxed.Long)
    eval("long - short2", long - short2, JavaBoxed.Long)
    eval("long - char", long - char, JavaBoxed.Long)
    eval("long - int2", long - int2, JavaBoxed.Long)
    eval("long - long2", long - long2, JavaBoxed.Long)
    eval("long - float", long - float, JavaBoxed.Float)
    eval("long - double", long - double, JavaBoxed.Double)
  }

  @Test
  def `float - sth`(): Unit = {
    eval("float - byte2", float - byte2, JavaBoxed.Float)
    eval("float - short2", float - short2, JavaBoxed.Float)
    eval("float - char", float - char, JavaBoxed.Float)
    eval("float - int2", float - int2, JavaBoxed.Float)
    eval("float - long2", float - long2, JavaBoxed.Float)
    eval("float - float2", float - float2, JavaBoxed.Float)
    eval("float - double", float - double, JavaBoxed.Double)
  }

  @Test
  def `double - sth`(): Unit = {
    eval("double - byte2", double - byte2, JavaBoxed.Double)
    eval("double - short2", double - short2, JavaBoxed.Double)
    eval("double - char", double - char, JavaBoxed.Double)
    eval("double - int2", double - int2, JavaBoxed.Double)
    eval("double - long2", double - long2, JavaBoxed.Double)
    eval("double - float", double - float, JavaBoxed.Double)
    eval("double - double2", double - double2, JavaBoxed.Double)
  }
}

object SubtractionTest extends BaseIntegrationTestCompanion
