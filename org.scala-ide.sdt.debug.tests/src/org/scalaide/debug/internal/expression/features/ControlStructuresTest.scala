/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.TestValues

class ControlStructuresTest extends BaseIntegrationTest(ControlStructuresTest) {

  import TestValues.Values._
  import TestValues.any2String

  @Test
  def ifElseCondition(): Unit = eval("if (true) byte + byte2 else byte", byte + byte2, JavaBoxed.Integer)

  @Test
  def ifElseIfElseConditions(): Unit =
    eval("if (int == int2) byte + byte2 else if (int != int) byte2 else byte", byte, JavaBoxed.Integer)

  @Test
  def nestedIfElseConditions(): Unit =
    eval("if (if (int == int) false else true) 1 else { if (int <= int) { if (false) 2 else { if (true) 3 else 4 } } else 5 }", 3, JavaBoxed.Integer)

  @Test
  def whileExpressionCondition(): Unit = eval("var i = 1; while (i > 1) i; i", 1, JavaBoxed.Integer)

  @Test
  def doWhileExpressionCondition(): Unit = eval("var i = 1; do i while (false); i", 1, JavaBoxed.Integer)

}

object ControlStructuresTest extends BaseIntegrationTestCompanion
