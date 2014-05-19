package org.scalaide.debug.internal.expression.features

import org.scalaide.debug.internal.expression.{JavaBoxed, BaseIntegrationTestCompanion, BaseIntegrationTest}
import org.junit.Test

/**
 * Author: Krzysztof Romanowski
 */
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