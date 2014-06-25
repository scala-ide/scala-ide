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


class AnyValTest extends BaseIntegrationTest(AnyValTest) {

  @Test
  def testTupleArrowCreation(): Unit = {
    eval(""" 1 -> 2 """, "(1,2)", "scala.Tuple2")
  }

  @Test
  def testCustomAnyVal(): Unit = {
    eval(""" 2.printMe """, "2", JavaBoxed.String)
    eval(""" 2.ala """, "ala", JavaBoxed.String)
  }

}

object AnyValTest extends BaseIntegrationTestCompanion