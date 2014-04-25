/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.primitives.operations

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues

class ComplexOperationsTest extends BaseIntegrationTest(ComplexOperationsTest) {

  import TestValues.Values._
  import TestValues.any2String

  @Test
  def multipleLogicalOperations(): Unit =
    eval(
      "true && (int + int2 == int + int || long2 == long2)",
      true && (int + int2 == int + int || long2 == long2),
      JavaBoxed.Boolean)

  @Test
  def multipleAddition(): Unit =
    eval("byte + byte2 + byte2", byte + byte2 + byte2, JavaBoxed.Integer)

  @Test
  def multiplePrimitiveOperations(): Unit =
    eval(
      "(int + long) * (int - int2) / double",
      (int + long) * (int - int2) / double,
      JavaBoxed.Double)

  @Test
  def multipleBitwiseOperations(): Unit =
    eval(
      "(int | long) ^ (int & int2) >> int >>> int2 << long",
      (int | long) ^ (int & int2) >> int >>> int2 << long,
      JavaBoxed.Long)

  @Test
  def testMixedBitwiseAndNumericalOperators() {
    eval("int << 2 + int", int << 2 + int, JavaBoxed.Integer)
    eval("int + 2 << int", int + 2 << int, JavaBoxed.Integer)
    eval("int & 2 + int", int & 2 + int, JavaBoxed.Integer)
    eval("int + 2 & int", int + 2 & int, JavaBoxed.Integer)
    eval("(short ^ 2) + byte2", (short ^ 2) + byte2, JavaBoxed.Integer)
    eval("(int & 2) + 1", (int & 2) + 1, JavaBoxed.Integer)
    eval("(long ^ 2) - 3", (long ^ 2) - 3, JavaBoxed.Long)
    eval("(int ^ 2) - int", (int ^ 2) - int, JavaBoxed.Integer)
    eval("(short2 << 2) - int", (short2 << 2) - int, JavaBoxed.Integer)
  }

  @Test
  def testMixedUnaryAndBitwiseOrNumericalOperators(): Unit = {
    eval("-int + +1", -int + +1, JavaBoxed.Integer)
    eval("-int ^ -2", -int ^ -2, JavaBoxed.Integer)
    eval("+int ^ +byte2", +int ^ +byte2, JavaBoxed.Integer)
    eval("-float * -long2", -float * -long2, JavaBoxed.Float)
    eval("+float * +long2", +float * +long2, JavaBoxed.Float)
    eval("(-int ^ 2) / 1 + double - float", (-int ^ 2) / 1 + double - float, JavaBoxed.Double)
  }

  @Test
  def testMixedConversionsAndBitwiseOrNumericalOperators(): Unit = {
    eval("(6.0 / double2).toLong ^ (float2 * short).toByte",
      (6.0 / double2).toLong ^ (float2 * short).toByte,
      JavaBoxed.Long)

    eval("((-double.toLong ^ -2) * byte * float).toShort",
      ((-double.toLong ^ -2) * byte * float).toShort,
      JavaBoxed.Short)
  }

  @Test
  def testComparisonForMixedOperations(): Unit =
    eval("((6.0 / double2).toLong ^ (float2 * short).toByte) < (-int ^ 2) / 1 + double - float",
      ((6.0 / double2).toLong ^ (float2 * short).toByte) < (-int ^ 2) / 1 + double - float,
      JavaBoxed.Boolean)

  @Ignore("TODO - O-5254 - Support for creation of Java boxed types using new")
  @Test
  def operationForJavaBoxedType(): Unit =
    eval("new java.lang.Integer(1) / 4.0 + new java.lang.Double(0.1)", 0.35, JavaBoxed.Double)
}

object ComplexOperationsTest extends BaseIntegrationTestCompanion
