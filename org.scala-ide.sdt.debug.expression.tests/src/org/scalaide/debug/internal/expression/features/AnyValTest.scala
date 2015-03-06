/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.junit.Ignore

class AnyValTest extends BaseIntegrationTest(AnyValTest) {

  @Test
  def testTupleArrowCreation(): Unit = {
    eval(""" 1 -> 2 """, "(1,2)", "scala.Tuple2")
  }

  @Ignore("TODO - O-7468 Add proper support for implicit conversion from Predef")
  @Test
  def testImplicitsMethodsFromPredef(): Unit = {
    eval(""" this.formatted("%s") """, "(1,2)", "scala.Tuple2")
    eval(""" this.ensuring(true, "Error") """, "(1,2)", "scala.Tuple2")
    eval(""" this.ensuring(_ => true, "Error") """, "(1,2)", "scala.Tuple2")
    eval(""" this.ensuring(true) """, "(1,2)", "scala.Tuple2")
    eval(""" this.ensuring(true) """, "(1,2)", "scala.Tuple2")

  }

  @Test
  def testCustomAnyVal(): Unit = {
    eval(""" 2.printMe """, "2", Java.boxed.String)
    eval(""" 2.ala """, "ala", Java.boxed.String)
  }

}

object AnyValTest extends BaseIntegrationTestCompanion