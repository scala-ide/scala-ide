/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.TestValues
import org.scalaide.debug.internal.expression.DefaultBeforeAfterEach

class UnaryOperatorsTest extends BaseIntegrationTest(UnaryOperatorsTest) with DefaultBeforeAfterEach {

  import TestValues.ValuesTestCase._

  @Test
  def `unary -`(): Unit = {
    eval("-float", -float, Java.primitives.float)
    eval("-double", -double, Java.primitives.double)
    eval("-int", -int, Java.primitives.int)
    eval("-long", -long, Java.primitives.long)
    eval("-byte", -byte, Java.primitives.int)
    eval("-short", -short, Java.primitives.int)
    eval("-char", -char, Java.primitives.int)
  }

  @Test
  def `unary +`(): Unit = {
    eval("+float", +float, Java.primitives.float)
    eval("+double", +double, Java.primitives.double)
    eval("+int", +int, Java.primitives.int)
    eval("+long", +long, Java.primitives.long)
    eval("+byte", +byte, Java.primitives.int)
    eval("+short", +short, Java.primitives.int)
    eval("+char", +char, Java.primitives.int)
  }

  @Test
  def `unary ~ (bitwise negation)`(): Unit = {
    expectReflectiveCompilationError("~float")
    expectReflectiveCompilationError("~double")
    eval("~int", ~int, Java.primitives.int)
    eval("~long", ~long, Java.primitives.long)
    eval("~byte", ~byte, Java.primitives.int)
    eval("~short", ~short, Java.primitives.int)
    eval("~char", ~char, Java.primitives.int)
  }
}

object UnaryOperatorsTest extends BaseIntegrationTestCompanion with DefaultBeforeAfterAll
