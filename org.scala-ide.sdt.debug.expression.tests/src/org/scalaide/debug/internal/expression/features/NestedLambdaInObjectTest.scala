package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TestValues.NestedLambdaInObjectTestCase

class NestedLambdaInObjectTest extends BaseIntegrationTest(NestedLambdaInObjectTest) {

  @Test(expected = classOf[RuntimeException]) // object A is not a package
  def shouldThrowExceptionBecauseLambdaInObjectIsNotImplemented(): Unit =
    eval("None.getOrElse(0)", "0", Java.primitives.int)

}

object NestedLambdaInObjectTest extends BaseIntegrationTestCompanion(NestedLambdaInObjectTestCase) {
  override protected def typeName: String = "debug.A$Inner1$Inner$"
}
