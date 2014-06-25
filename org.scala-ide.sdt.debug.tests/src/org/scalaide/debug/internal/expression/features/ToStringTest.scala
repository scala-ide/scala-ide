/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.TestValues

class ToStringTest extends BaseIntegrationTest(ToStringTest) {

  import TestValues._

  @Test
  def toStringWithParentheses(): Unit = eval("int.toString()", Values.int, JavaBoxed.String)

  @Test
  def toStringWithoutParentheses(): Unit = eval("int.toString", Values.int, JavaBoxed.String)

  @Test
  def toStringOnObject(): Unit = eval("list.toString", Values.list, JavaBoxed.String)

  @Test
  def toStringOnString(): Unit = eval("string.toString", Values.string, JavaBoxed.String)

}

object ToStringTest extends BaseIntegrationTestCompanion
