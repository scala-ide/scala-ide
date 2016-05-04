/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala

class MultipleParametersListTest extends BaseIntegrationTest(MultipleParametersListTest) {

  @Test
  def testMultipleParametersClassMethod(): Unit =
    eval("(new LibVarargs.LibClassVarargs(1)()).sub(Nat3)(1)", 2, Java.primitives.int)

  @Test
  def testMultipleParametersClassMethodFirstVararg(): Unit =
    eval("(new LibVarargs.LibClassVarargs(4)(2)).mul(3, 4, 1)(Nat2)", List(6, 8, 2), Scala.::)

  @Test
  def testMultipleParametersClassMethodLastVararg(): Unit =
    eval("(new LibVarargs.LibClassVarargs(8)(4, 7)).sum(Nat1)(3, 4, 1)", List(4, 5, 2), Scala.::)

  @Test
  def testMultipleParametersClassMethodLastImplicit(): Unit =
    eval("(new LibVarargs.LibClassVarargs(8)(4, 7)).product(1,2)", List(1, 2, 2, 4), Scala.::)

  @Test
  def testMultipleParametersObjectMethod(): Unit =
    eval("debug.LibVarargs.LibObjectVarargs.sub(Nat3)(1)", 2, Java.primitives.int)

  @Test
  def testMultipleParametersObjectMethodFirstVararg(): Unit =
    eval("debug.LibVarargs.LibObjectVarargs.mul(3, 4, 1)(Nat2)", List(6, 8, 2), Scala.::)

  @Test
  def testMultipleParametersObjectMethodLastVararg(): Unit =
    eval("debug.LibVarargs.LibObjectVarargs.sum(Nat1)(3, 4, 1)", List(4, 5, 2), Scala.::)

  @Test
  def testMultipleParametersObjectMethodLastImplicit(): Unit =
    eval("debug.LibVarargs.LibObjectVarargs.product(1,2)", List(1, 2, 2, 4), Scala.::)

  @Test
  def testMultipleParametersFunction(): Unit =
    eval("Libs.libMultipleParamers(int)(int)", 2, Java.primitives.int)

  @Test
  def testFullNameMultipleParametersMethod(): Unit =
    eval("debug.Libs.libMultipleParamers(int)(int)", 2, Java.primitives.int)

  @Ignore("TODO - O-8563 - Add support for varargs with multiple parameters list")
  @Test
  def testThreeParameterLists(): Unit =
    eval("(new LibVarargs.LibVarargs).sum(6)(Nat3)(1, 4, 2)", 16, Java.primitives.int)
}

object MultipleParametersListTest extends BaseIntegrationTestCompanion with DefaultBeforeAfterAll
