/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.DefaultBeforeAfterEach

class PrimitivesIntegrationTest extends BaseIntegrationTest(PrimitivesIntegrationTest) with DefaultBeforeAfterEach {

  @Test
  def testIntMethodAccess(): Unit = eval("list(int)", 2, Java.primitives.int)

  @Test
  def testDoubleMethodAccess(): Unit = eval("Double.box(double)", 1.1, Java.primitives.double)

  @Test
  def testFloatMethodAccess(): Unit = eval("Float.box(float)", 1.1, Java.primitives.float)

  @Test
  def testCharMethodAccess(): Unit = eval("Char.box(char)", 'c', Java.primitives.char)

  @Test
  def testBooleanMethodAccess(): Unit = eval("Boolean.box(boolean)", false, Java.primitives.boolean)

  @Test
  def testLongMethodAccess(): Unit = eval("Long.box(long)", 1, Java.primitives.long)

}

object PrimitivesIntegrationTest extends BaseIntegrationTestCompanion with DefaultBeforeAfterAll
