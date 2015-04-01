/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues

class ComplexOperationsTest extends BaseIntegrationTest(ComplexOperationsTest) {

  import TestValues.ValuesTestCase._

  @Test
  def multipleLogicalOperations(): Unit =
    eval(
      "true && (int + int2 == int + int || long2 == long2)",
      true && (int + int2 == int + int || long2 == long2),
      Java.boxed.Boolean)

  @Test
  def multipleAddition(): Unit =
    eval("byte + byte2 + byte2", byte + byte2 + byte2, Java.boxed.Integer)

  @Test
  def multiplePrimitiveOperations(): Unit =
    eval(
      "(int + long) * (int - int2) / double",
      (int + long) * (int - int2) / double,
      Java.boxed.Double)

  @Test
  def multipleBitwiseOperations(): Unit =
    eval(
      "(int | long) ^ (int & int2) >> int >>> int2 << long",
      (int | long) ^ (int & int2) >> int >>> int2 << long,
      Java.boxed.Long)

  @Test
  def testMixedBitwiseAndNumericalOperators() {
    eval("int << 2 + int", int << 2 + int, Java.boxed.Integer)
    eval("int + 2 << int", int + 2 << int, Java.boxed.Integer)
    eval("int & 2 + int", int & 2 + int, Java.boxed.Integer)
    eval("int + 2 & int", int + 2 & int, Java.boxed.Integer)
    eval("(short ^ 2) + byte2", (short ^ 2) + byte2, Java.boxed.Integer)
    eval("(int & 2) + 1", (int & 2) + 1, Java.boxed.Integer)
    eval("(long ^ 2) - 3", (long ^ 2) - 3, Java.boxed.Long)
    eval("(int ^ 2) - int", (int ^ 2) - int, Java.boxed.Integer)
    eval("(short2 << 2) - int", (short2 << 2) - int, Java.boxed.Integer)
  }

  @Test
  def testMixedUnaryAndBitwiseOrNumericalOperators(): Unit = {
    eval("-int + +1", -int + +1, Java.boxed.Integer)
    eval("-int ^ -2", -int ^ -2, Java.boxed.Integer)
    eval("+int ^ +byte2", +int ^ +byte2, Java.boxed.Integer)
    eval("-float * -long2", -float * -long2, Java.boxed.Float)
    eval("+float * +long2", +float * +long2, Java.boxed.Float)
    eval("(-int ^ 2) / 1 + double - float", (-int ^ 2) / 1 + double - float, Java.boxed.Double)
  }

  @Test
  def testMixedConversionsAndBitwiseOrNumericalOperators(): Unit = {
    eval("(6.0 / double2).toLong ^ (float2 * short).toByte",
      (6.0 / double2).toLong ^ (float2 * short).toByte,
      Java.boxed.Long)

    eval("((-double.toLong ^ -2) * byte * float).toShort",
      ((-double.toLong ^ -2) * byte * float).toShort,
      Java.boxed.Short)
  }

  @Test
  def testComparisonForMixedOperations(): Unit =
    eval("((6.0 / double2).toLong ^ (float2 * short).toByte) < (-int ^ 2) / 1 + double - float",
      ((6.0 / double2).toLong ^ (float2 * short).toByte) < (-int ^ 2) / 1 + double - float,
      Java.boxed.Boolean)

  @Test
  def operationForJavaBoxedType(): Unit =
    eval("new java.lang.Integer(1) / 4.0 + new java.lang.Double(0.1)", 0.35, Java.boxed.Double)
}

object ComplexOperationsTest extends BaseIntegrationTestCompanion
