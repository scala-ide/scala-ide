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

class PrimitivesIntegrationTest extends BaseIntegrationTest(PrimitivesIntegrationTest) {

  @Test
  def testIntMethodAccess(): Unit = eval("list(int)", "2", JavaBoxed.Integer)

  @Test
  def testDoubleMethodAccess(): Unit = eval("Double.box(double)", "1.1", JavaBoxed.Double)

  @Test
  def testFloatMethodAccess(): Unit = eval("Float.box(float)", "1.1", JavaBoxed.Float)

  @Test
  def testCharMethodAccess(): Unit = eval("Char.box(char)", "c", JavaBoxed.Character)

  @Test
  def testBooleanMethodAccess(): Unit = eval("Boolean.box(boolean)", "false", JavaBoxed.Boolean)

  @Test
  def testLongMethodAccess(): Unit = eval("Long.box(long)", "1", JavaBoxed.Long)

}

object PrimitivesIntegrationTest extends BaseIntegrationTestCompanion