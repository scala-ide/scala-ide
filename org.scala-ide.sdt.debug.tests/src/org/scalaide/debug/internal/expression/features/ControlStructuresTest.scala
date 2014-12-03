/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TestValues
import org.scalaide.debug.internal.expression.UnsupportedFeature

class ControlStructuresTest extends BaseIntegrationTest(ControlStructuresTest) {

  import TestValues.ValuesTestCase._
  import TestValues.any2String

  // TODO - O-5919 - implement support for isInstanceOf
  @Test(expected = classOf[UnsupportedFeature])
  def isInstanceOf(): Unit = eval("int.isInstanceOf[Int]", int.isInstanceOf[Int], Java.boxed.Boolean)

  // TODO - O-5919 - implement support for asInstanceOf
  @Test(expected = classOf[UnsupportedFeature])
  def asInstanceOf(): Unit = eval("int.asInstanceOf[Int]", int.asInstanceOf[Int], Java.boxed.Integer)

  @Test
  def ifElseCondition(): Unit = eval("if (true) byte + byte2 else byte", byte + byte2, Java.boxed.Integer)

  @Test
  def ifElseIfElseConditions(): Unit =
    eval("if (int == int2) byte + byte2 else if (int != int) byte2 else byte", byte, Java.boxed.Integer)

  @Test
  def nestedIfElseConditions(): Unit =
    eval("if (if (int == int) false else true) 1 else { if (int <= int) { if (false) 2 else { if (true) 3 else 4 } } else 5 }", 3, Java.boxed.Integer)

  @Test
  def whileExpressionCondition(): Unit = eval("var i = 1; while (i > 1) i; i", 1, Java.boxed.Integer)

  @Test
  def doWhileExpressionCondition(): Unit = eval("var i = 1; do i while (false); i", 1, Java.boxed.Integer)

  // TODO - O-5626 - support for try and throw
  @Test(expected = classOf[UnsupportedFeature])
  def tryCatchFinally(): Unit =
    eval("""try { 1 } finally { 2 }""", 2, Java.boxed.Integer)

}

object ControlStructuresTest extends BaseIntegrationTestCompanion
