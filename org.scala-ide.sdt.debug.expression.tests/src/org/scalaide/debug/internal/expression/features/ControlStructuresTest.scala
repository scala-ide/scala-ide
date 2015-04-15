/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala

class ControlStructuresTest extends BaseIntegrationTest(ControlStructuresTest) {

  import TestValues.ValuesTestCase._

  @Test
  def ifElseCondition(): Unit = eval("if (true) byte + byte2 else byte", byte + byte2, Java.primitives.int)

  @Test
  def ifElseIfElseConditions(): Unit =
    eval("if (int == int2) byte + byte2 else if (int != int) byte2 else byte", byte, Java.primitives.int)

  @Test
  def nestedIfElseConditions(): Unit =
    eval("if (if (int == int) false else true) 1 else { if (int <= int) { if (false) 2 else { if (true) 3 else 4 } } else 5 }", 3, Java.primitives.int)

  @Test
  def whileExpressionCondition(): Unit = eval("var i = 1; while (i > 1) i; i", 1, Java.primitives.int)

  @Test
  def doWhileExpressionCondition(): Unit = eval("var i = 1; do i while (false); i", 1, Java.primitives.int)

  // TODO - O-8599 - support for return
  @Test(expected = classOf[UnsupportedFeature])
  def returnFrom(): Unit = eval("return 123", "", "")

  // TODO - O-8597 - support for super calls
  @Test(expected = classOf[UnsupportedFeature])
  def superCall(): Unit = eval("super.foo()", "", "")

  // TODO - O-8598 - support for pattern matching
  @Test(expected = classOf[UnsupportedFeature])
  def patternMatch(): Unit = eval("int match { case i: Int => i } ", "", "")

  @Test(expected = classOf[ThrowDetected])
  def throwException(): Unit = eval("""throw new RuntimeException("Boo!")""", "", "")

  // TODO - O-8596 - support for try/catch/finally
  @Test(expected = classOf[UnsupportedFeature])
  def tryCatchFinally(): Unit =
    eval("""try { 1 } finally { 2 }""", 2, Java.primitives.int)

  @Test
  def simpleForComprehension(): Unit =
    eval("""for { i <- list } yield i""", for { i <- list } yield i, Scala.::)

  @Test
  def filteredForComprehension(): Unit =
    eval("""for { i <- list; if i % 2 == 0 } yield i""", for { i <- list; if i % 2 == 0 } yield i, Scala.::)

  @Test
  def forComprehensionWithVal(): Unit = {
    eval("""for { i <- list; j = i.toString } yield j""", for { i <- list; j = i.toString } yield j, Scala.::)
    eval("""for { i <- list; val j = i.toString } yield j""", for { i <- list; val j = i.toString } yield j, Scala.::)
  }

  @Test
  def nestedForComprehension(): Unit =
    eval("""for { i <- list; j <- list } yield (i,j)""", for { i <- list; j <- list } yield (i, j), Scala.::)

  @Test
  def simpleForLoop(): Unit =
    eval("""for { i <- list } i""", (), Scala.unitType)

  @Test
  def filteredForLoop(): Unit =
    eval("""for { i <- list; if i % 2 == 0 } i""", (), Scala.unitType)

  @Test
  def nestedForLoop(): Unit =
    eval("""for { i <- list; j <- list } (i,j)""", (), Scala.unitType)

  @Test
  def forLoopWithVal(): Unit = {
    eval("""for { i <- list; j = i.toString } j""", (), Scala.unitType)
    eval("""for { i <- list; val j = i.toString } j""", (), Scala.unitType)
  }

  @Test
  def singleVal(): Unit =
    eval("val a = 1", (), Scala.unitType)

  // TODO - O-8580 - add support for lazy vals in evaluated expression
  @Test(expected = classOf[UnsupportedFeature])
  def singleLazyVal(): Unit =
    eval("lazy val a: Int = 1", (), Scala.unitType)

  @Test
  def singleDef(): Unit =
    eval("def foo() = 1", (), Scala.unitType)

}

object ControlStructuresTest extends BaseIntegrationTestCompanion
