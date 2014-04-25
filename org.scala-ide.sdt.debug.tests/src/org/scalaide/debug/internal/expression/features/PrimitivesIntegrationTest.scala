/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest

class PrimitivesIntegrationTest extends BaseIntegrationTest(PrimitivesIntegrationTest) {

  @Test
  def testIntMethodAccess(): Unit = eval("list(int)", "2", Java.boxed.Integer)

  @Test
  def testDoubleMethodAccess(): Unit = eval("Double.box(double)", "1.1", Java.boxed.Double)

  @Test
  def testFloatMethodAccess(): Unit = eval("Float.box(float)", "1.1", Java.boxed.Float)

  @Test
  def testCharMethodAccess(): Unit = eval("Char.box(char)", "c", Java.boxed.Character)

  @Test
  def testBooleanMethodAccess(): Unit = eval("Boolean.box(boolean)", "false", Java.boxed.Boolean)

  @Test
  def testLongMethodAccess(): Unit = eval("Long.box(long)", "1", Java.boxed.Long)

}

object PrimitivesIntegrationTest extends BaseIntegrationTestCompanion