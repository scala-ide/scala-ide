package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TestValues.NestedLambdaTestCase
import org.scalaide.debug.internal.expression.DefaultBeforeAfterEach

class NestedLambdaTest extends BaseIntegrationTest(NestedLambdaTest) with DefaultBeforeAfterEach {

  @Test
  def shouldEvaluateWhateverEvenIfExecutedInNestedLambda(): Unit =
    eval("None.getOrElse(0)", "0", Java.primitives.int)

}

object NestedLambdaTest extends BaseIntegrationTestCompanion(NestedLambdaTestCase) with DefaultBeforeAfterAll {
  override protected def typeName: String = "debug.A$Inner1$Inner"
}
