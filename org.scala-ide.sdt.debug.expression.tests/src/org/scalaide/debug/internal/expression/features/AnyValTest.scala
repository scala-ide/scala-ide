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
    eval(""" 1 -> 2 """, (1, 2), "scala.Tuple2")
    eval(""" (new ArrowAssoc(1))->(2) """, (1, 2), "scala.Tuple2")
  }

  @Test
  def testImplicitsMethodsFromPredef(): Unit = {
    eval(""" 1 -> 2 """, (1, 2), "scala.Tuple2")
    eval(""" (1 -> 2).formatted("%s") """, (1, 2), Java.boxed.String)
    eval(""" (1 -> 2).ensuring(true, "Error") """, (1, 2), "scala.Tuple2")
    eval(""" (1 -> 2).ensuring(_ => true, "Error") """, (1, 2), "scala.Tuple2")
    eval(""" (1 -> 2).ensuring(true) """, (1, 2), "scala.Tuple2")
    eval(""" 6 min 4 """, 4, Java.boxed.Integer)
    eval(""" 1 + "2" """, 12, Java.boxed.String)
    eval("(-1).abs", 1, Java.boxed.Integer)
  }

  @Test
  def testCustomAnyVal(): Unit = {
    eval(""" 2.printMe """, 2, Java.boxed.String)
    eval(""" 2.ala """, "ala", Java.boxed.String)
  }

}

object AnyValTest extends BaseIntegrationTestCompanion
