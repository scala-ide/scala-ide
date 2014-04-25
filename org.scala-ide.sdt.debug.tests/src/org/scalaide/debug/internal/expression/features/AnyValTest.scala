/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest

class AnyValTest extends BaseIntegrationTest(AnyValTest) {

  @Test
  def testTupleArrowCreation(): Unit = {
    eval(""" 1 -> 2 """, "(1,2)", "scala.Tuple2")
  }

  @Test
  def testCustomAnyVal(): Unit = {
    eval(""" 2.printMe """, "2", Java.boxed.String)
    eval(""" 2.ala """, "ala", Java.boxed.String)
  }

}

object AnyValTest extends BaseIntegrationTestCompanion