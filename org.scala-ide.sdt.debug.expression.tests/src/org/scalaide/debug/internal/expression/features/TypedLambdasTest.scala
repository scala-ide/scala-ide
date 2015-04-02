/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala

class TypedLambdasTest extends BaseIntegrationTest(TypedLambdasTest) {

  // TODO - this test fails with assertion from toolbox compiler when `TypedLambdasTest` is run separately
  // Grzegorz Kossakowski said it's a bug in Scala and we could investigate it further and file a ticket
  @Test
  def testMapWithExplicitType(): Unit = disableOnJava8 {
    eval("list.map((_: Int) - 1)", List(0, 1, 2), Scala.::)
  }

  @Test
  def testFilterWithExplicitType(): Unit = disableOnJava8 {
    eval("list.filter((_: Int) > 1)", List(2, 3), Scala.::)
  }

  @Test
  def testMapWithExplicitTypeAndClosure(): Unit = disableOnJava8 {
    eval("list.map((_: Int) - int)", List(0, 1, 2), Scala.::)
  }

  @Test
  def testLambdaWithExplicitTypeAndClosureThatRequiresReturnValue(): Unit = disableOnJava8 {
    eval("list.map((_: Int) - int).head + 1", 1, Java.boxed.Integer)
  }

  @Test
  def testTypedLambdaBeforeNormalLambda(): Unit = disableOnJava8 {
    eval("list.map((_: Int).toString).map(_.size)", List(1, 1, 1), Scala.::)
  }

  @Test
  def testLambdaWithExplicitTypeAndClosureThatRequiresReturnValueWithBigScope(): Unit = disableOnJava8 {
    eval(
      """
        |val diff = 2
        |val ala = {
        | (list.map((_: Int) - diff).head + 1).toString
        |}
        |ala
        |""".stripMargin, "0", Java.boxed.String)
  }

  @Test
  def testLambdaWithExplicitTypeThatRequiresReturnValue(): Unit =
    eval("list.map((_: Int) - 1).sum", 3, Java.boxed.Integer)

  @Test
  def testLambdaWithPartialFunction(): Unit = disableOnJava8 {
    eval("list.map{ case i: Int => i - int}", List(0, 1, 2), Scala.::)
  }

  @Test
  def testLambdaWithPartialFunctionAndImportantReturnType(): Unit = disableOnJava8 {
    eval("list.map{ case i: Int => i - int}.head + 1", 1, Java.boxed.Integer)
  }

  @Test
  def testHigherOrderfunctionWithMultipleParameterListsOnValue(): Unit = disableOnJava8 {
    eval("list.fold(0)((_: Int) + (_: Int))", 6, Java.boxed.Integer)
  }

}

object TypedLambdasTest extends BaseIntegrationTestCompanion
