/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues

class UnaryOperatorsTest extends BaseIntegrationTest(UnaryOperatorsTest) {

  import TestValues.ValuesTestCase._

  @Test
  def `unary -`(): Unit = {
    eval("-float", -float, Java.boxed.Float)
    eval("-double", -double, Java.boxed.Double)
    eval("-int", -int, Java.boxed.Integer)
    eval("-long", -long, Java.boxed.Long)
    eval("-byte", -byte, Java.boxed.Integer)
    eval("-short", -short, Java.boxed.Integer)
    eval("-char", -char, Java.boxed.Integer)
  }

  @Test
  def `unary +`(): Unit = {
    eval("+float", +float, Java.boxed.Float)
    eval("+double", +double, Java.boxed.Double)
    eval("+int", +int, Java.boxed.Integer)
    eval("+long", +long, Java.boxed.Long)
    eval("+byte", +byte, Java.boxed.Integer)
    eval("+short", +short, Java.boxed.Integer)
    eval("+char", +char, Java.boxed.Integer)
  }

  @Test
  def `unary ~ (bitwise negation)`(): Unit = {
    expectReflectiveCompilationError("~float")
    expectReflectiveCompilationError("~double")
    eval("~int", ~int, Java.boxed.Integer)
    eval("~long", ~long, Java.boxed.Long)
    eval("~byte", ~byte, Java.boxed.Integer)
    eval("~short", ~short, Java.boxed.Integer)
    eval("~char", ~char, Java.boxed.Integer)
  }
}

object UnaryOperatorsTest extends BaseIntegrationTestCompanion
