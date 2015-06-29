/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

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
      Java.primitives.boolean)

  @Test
  def multipleAddition(): Unit =
    eval("byte + byte2 + byte2", byte + byte2 + byte2, Java.primitives.int)

  @Test
  def multiplePrimitiveOperations(): Unit =
    eval(
      "(int + long) * (int - int2) / double",
      (int + long) * (int - int2) / double,
      Java.primitives.double)

  @Test
  def multipleBitwiseOperations(): Unit =
    eval(
      "(int | long) ^ (int & int2) >> int >>> int2 << long",
      (int | long) ^ (int & int2) >> int >>> int2 << long,
      Java.primitives.long)

  @Test
  def testMixedBitwiseAndNumericalOperators(): Unit = {
    eval("int << 2 + int", int << 2 + int, Java.primitives.int)
    eval("int + 2 << int", int + 2 << int, Java.primitives.int)
    eval("int & 2 + int", int & 2 + int, Java.primitives.int)
    eval("int + 2 & int", int + 2 & int, Java.primitives.int)
    eval("(short ^ 2) + byte2", (short ^ 2) + byte2, Java.primitives.int)
    eval("(int & 2) + 1", (int & 2) + 1, Java.primitives.int)
    eval("(long ^ 2) - 3", (long ^ 2) - 3, Java.primitives.long)
    eval("(int ^ 2) - int", (int ^ 2) - int, Java.primitives.int)
    eval("(short2 << 2) - int", (short2 << 2) - int, Java.primitives.int)
  }

  @Test
  def testMixedUnaryAndBitwiseOrNumericalOperators(): Unit = {
    eval("-int + +1", -int + +1, Java.primitives.int)
    eval("-int ^ -2", -int ^ -2, Java.primitives.int)
    eval("+int ^ +byte2", +int ^ +byte2, Java.primitives.int)
    eval("-float * -long2", -float * -long2, Java.primitives.float)
    eval("+float * +long2", +float * +long2, Java.primitives.float)
    eval("(-int ^ 2) / 1 + double - float", (-int ^ 2) / 1 + double - float, Java.primitives.double)
  }

  @Test
  def testMixedConversionsAndBitwiseOrNumericalOperators(): Unit = {
    eval("(6.0 / double2).toLong ^ (float2 * short).toByte",
      (6.0 / double2).toLong ^ (float2 * short).toByte,
      Java.primitives.long)

    eval("((-double.toLong ^ -2) * byte * float).toShort",
      ((-double.toLong ^ -2) * byte * float).toShort,
      Java.primitives.short)
  }

  @Test
  def testComparisonForMixedOperations(): Unit =
    eval("((6.0 / double2).toLong ^ (float2 * short).toByte) < (-int ^ 2) / 1 + double - float",
      ((6.0 / double2).toLong ^ (float2 * short).toByte) < (-int ^ 2) / 1 + double - float,
      Java.primitives.boolean)

  @Test
  def operationForJavaBoxedType(): Unit =
    eval("new java.lang.Integer(1) / 4.0 + new java.lang.Double(0.1)", 0.35, Java.primitives.double)
}

object ComplexOperationsTest extends BaseIntegrationTestCompanion
