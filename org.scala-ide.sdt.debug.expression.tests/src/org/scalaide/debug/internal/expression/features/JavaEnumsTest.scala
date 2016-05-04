/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TestValues

class JavaEnumsTest extends BaseIntegrationTest(JavaEnumsTest) {

  @Test
  def accessingEnumValues(): Unit = {
    eval("JavaEnum.FOO.name()", "FOO", Java.String)
    eval("JavaEnum.BAZ.ordinal()", 2, Java.primitives.int)
  }

  @Test
  def equalityOfEnumValues(): Unit = {
    eval("JavaEnum.FOO == JavaEnum.FOO", true.toString(), Java.primitives.boolean)
    eval("JavaEnum.BAZ == JavaEnum.FOO", false.toString(), Java.primitives.boolean)
    eval("JavaEnum.FOO != JavaEnum.FOO", false.toString(), Java.primitives.boolean)
    eval("JavaEnum.BAZ != JavaEnum.FOO", true.toString(), Java.primitives.boolean)
  }
}

object JavaEnumsTest extends BaseIntegrationTestCompanion(TestValues.JavaTestCase) with DefaultBeforeAfterAll
