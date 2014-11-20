/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala

class LambdasTest extends BaseIntegrationTest(LambdasTest) {

  @Test
  def `(x: Int) => int + x`(): Unit =
    eval(" (x : Int) => int + x", "<function1>", Debugger.lambdaType)

  @Test
  def `(x: Int) => int.toString.mkString `(): Unit =
    eval(" (x : Int) => x.toString.mkString ", "<function1>", Debugger.lambdaType)

  @Test
  def `(x: Int, y: Int) => x + y `(): Unit =
    eval(" (x: Int, y: Int) => x + y ", "<function2>", Debugger.lambdaType)

  @Test
  def `((x: Int) => x.toString.mkString)(2) `(): Unit =
    eval(" ((x: Int) => x.toString.mkString)(2) ", "2", Java.boxed.String)

  @Test
  def higherOrderFunctionWithMultipleParameterLists(): Unit =
    eval("List(1, 2, 3).fold(0)(_ + _)", "6", Java.boxed.Integer)

  @Test
  def simpleLambdaWithWrittenTypes(): Unit =
    eval("list.map((_: Int) + 1)", "List(2, 3, 4)", Scala.::)

  @Test
  def `function and primitives: list.filter(_ >= 2) `(): Unit =
    eval("list.filter(_ >= 2)", "List(2, 3)", Scala.::)

  @Test
  def `function and primitives: list.filter(1 <) `(): Unit =
    eval("list.filter(1 <)", "List(2, 3)", Scala.::)

  @Test
  def `libClass.perform(_ + 2) `(): Unit =
    eval("libClass.perform(_ + 2)", "3", Java.boxed.Integer)

  @Test
  def `libClass.performByName(1 + 2) `(): Unit =
    eval("libClass.performByName(1 + 2)", "4", Java.boxed.Integer)

  @Test
  def `libClass.performTwice(libClass.incrementAndGet()) `(): Unit =
    eval(" libClass.performTwice(libClass.incrementAndGet()) ", "5", Java.boxed.Integer)

  @Test
  def mappingOnFullType(): Unit = {
    eval("multilist.map( (_: collection.immutable.List[Any]).toString)", "List(List(1), List(2, 3))", Scala.::)
    eval("multilist.map( (list: collection.immutable.List[_]) => list.toString)", "List(List(1), List(2, 3))", Scala.::)
  }

  @Test
  def `libClass.performByName("ala".mkString) `(): Unit =
    eval(""" libClass.performByNameGen("ala".mkString) """, "ala", Java.boxed.String)

    @Ignore("")
  @Test
  def `lambda inside lambda over collection: multilist.map(list => list.map(_ + 1))`(): Unit =
    eval(""" multilist.map(list => list.map(_ + 1)) """, "List(List(2), List(3, 4))", Scala.::)

  @Ignore("TODO - O-5770 - add support for nested lambdas")
  @Test
  def `libClass.performOnList(list => list.map(_ + 1))`(): Unit =
    eval(""" libClass.performOnList(list => list.map(_ + 1)) """, "List(2, 3, 4)", Scala.::)
}

object LambdasTest extends BaseIntegrationTestCompanion