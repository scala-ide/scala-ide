/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java

class JavaStaticMethodsTest extends BaseIntegrationTest(JavaStaticMethodsTest) {

  @Ignore("TODO - O-6272 - support for Java static methods")
  @Test
  def staticFieldsOnClass(): Unit = {
    eval("JavaLibClass.staticString", "staticString", Java.boxed.String)
    eval("JavaLibClass.staticInt", "staticInt", Java.boxed.Integer)
  }

  @Ignore("TODO - O-6272 - support for Java static methods")
  @Test
  def staticFieldsOnInterface(): Unit = {
    eval("JavaLibInterface.staticString", "staticString", Java.boxed.String)
    eval("JavaLibInterface.staticInt", "staticInt", Java.boxed.Integer)
  }

}

object JavaStaticMethodsTest extends BaseIntegrationTestCompanion
