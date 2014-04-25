/*
 * Copyright (c) 2014 Contributor. All rights reserved.
*/
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.junit.Ignore

import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.TestValues.InnerMethodsTestCase

class InnerMethodsTest extends BaseIntegrationTest(InnerMethodsTest) {

  @Ignore("TODO - O-5256 - Support for inner method")
  @Test
  def callInnerMethods(): Unit = {
    eval("innerMethod", "ala", Java.boxed.String)
    eval( """innerMethod2("prefix_")""", "prefix_ala", Java.boxed.String)
  }

}

object InnerMethodsTest extends BaseIntegrationTestCompanion(
  fileName = InnerMethodsTestCase.fileName,
  lineNumber = InnerMethodsTestCase.breakpointLine)
