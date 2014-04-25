/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.junit.Test
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues

class UnaryOperatorsTest extends BaseIntegrationTest(UnaryOperatorsTest) {

  import TestValues.Values._
  import TestValues.any2String

  @Test
  def `unary -`(): Unit = {
    eval("-float", -float, JavaBoxed.Float)
    eval("-double", -double, JavaBoxed.Double)
    eval("-int", -int, JavaBoxed.Integer)
    eval("-long", -long, JavaBoxed.Long)
    eval("-byte", -byte, JavaBoxed.Integer)
    eval("-short", -short, JavaBoxed.Integer)
    eval("-char", -char, JavaBoxed.Integer)
  }

  @Test
  def `unary +`(): Unit = {
    eval("+float", +float, JavaBoxed.Float)
    eval("+double", +double, JavaBoxed.Double)
    eval("+int", +int, JavaBoxed.Integer)
    eval("+long", +long, JavaBoxed.Long)
    eval("+byte", +byte, JavaBoxed.Integer)
    eval("+short", +short, JavaBoxed.Integer)
    eval("+char", +char, JavaBoxed.Integer)
  }

  @Test
  def `unary ~ (bitwise negation)`(): Unit = {
    evalWithToolboxError("~float")
    evalWithToolboxError("~double")
    eval("~int", ~int, JavaBoxed.Integer)
    eval("~long", ~long, JavaBoxed.Long)
    eval("~byte", ~byte, JavaBoxed.Integer)
    eval("~short", ~short, JavaBoxed.Integer)
    eval("~char", ~char, JavaBoxed.Integer)
  }
}

object UnaryOperatorsTest extends BaseIntegrationTestCompanion