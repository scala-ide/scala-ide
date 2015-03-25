/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Debugger
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TestValues

class LambdasTest extends BaseIntegrationTest(LambdasTest) {

  import TestValues.ValuesTestCase._
  import TestValues.any2String

  @Test
  def `(x: Int) => int + x`(): Unit =
    eval(" (x : Int) => int + x", "<function1>", Debugger.lambdaType)

  @Test
  def `(x: Int) => int.toString.mkString `(): Unit = disableOnJava8 {
    eval(" (x : Int) => x.toString.mkString ", "<function1>", Debugger.lambdaType)
  }

  @Test
  def `(x: Int, y: Int) => x + y `(): Unit =
    eval(" (x: Int, y: Int) => x + y ", "<function2>", Debugger.lambdaType)

  @Test
  def `((x: Int) => x.toString.mkString)(2) `(): Unit = disableOnJava8 {
    eval(" ((x: Int) => x.toString.mkString)(2) ", "2", Java.boxed.String)
  }

  @Test
  def higherOrderFunctionWithMultipleParameterLists(): Unit = disableOnJava8 {
    eval("List(1, 2, 3).fold(0)(_ + _)", "6", Java.boxed.Integer)
  }

  @Test
  def simpleLambdaWithWrittenTypes(): Unit = disableOnJava8 {
    eval("list.map((_: Int) + 1)", list.map((_: Int) + 1), Scala.::)
  }

  @Test
  def `function and primitives: list.filter(_ >= 2) `(): Unit = disableOnJava8 {
    eval("list.filter(_ >= 2)", list.filter(_ >= 2), Scala.::)
  }

  @Test
  def `function and primitives: list.filter(1 <) `(): Unit = disableOnJava8 {
    eval("list.filter(1 <)", list.filter(1 <), Scala.::)
  }

  @Test
  def `libClass.perform(_ + 2) `(): Unit =
    eval("libClass.perform(_ + 2)", "3", Java.boxed.Integer)

  @Test
  def `libClass.performByName(1 + 2) `(): Unit = disableOnJava8 {
    eval("libClass.performByName(1 + 2)", "4", Java.boxed.Integer)
  }

  @Test
  def `libClass.performTwice(libClass.incrementAndGet()) `(): Unit =
    eval(" libClass.performTwice(libClass.incrementAndGet()) ", "5", Java.boxed.Integer)

  @Test
  def mappingOnFullType(): Unit = disableOnJava8 {
    eval("multilist.map( (_: collection.immutable.List[Any]).toString)",
      multilist.map((_: collection.immutable.List[Any]).toString), Scala.::)
    eval("multilist.map( (list: collection.immutable.List[_]) => list.toString)",
      multilist.map((list: collection.immutable.List[_]) => list.toString), Scala.::)
  }

  @Test
  def `libClass.performByName("ala".mkString) `(): Unit =
    eval(""" libClass.performByNameGen("ala".mkString) """, "ala", Java.boxed.String)

  @Test
  def `lambda inside lambda over collection: multilist.map(list => list.map(_ + 1))`(): Unit =
    eval(""" multilist.map(list => list.map(_ + 1)) """, multilist.map(list => list.map(_ + 1)), Scala.::)

  @Test
  def objectTypedArgument(): Unit =
    eval(""" objectList.map(list => list.value) """, "List(11, 11)", Scala.::)

  @Test
  def genericTypedArgument(): Unit =
    eval(""" multilist.map(list => list.sum) """, multilist.map(list => list.sum), Scala.::)

  @Test
  def `lambda inside lambda inside lambda: multilist.map(list => list.map(_ + 1))`(): Unit =
    eval(""" multilist.map(list => list.map(int => list.map(_ + int).sum)) """, multilist.map(list => list.map(int => list.map(_ + int).sum)), Scala.::)

  @Test
  def `libClass.performOnList(list => list.map(_ + 1))`(): Unit =
    eval(""" libClass.performOnList(list => list.map(_ + 1)) """, "List(2, 3)", Scala.::)

  //Lambda closure params

  @Test
  def genricClosureParam(): Unit =
    eval("""list.flatMap { i => list.map { j => (i,j) } }""", list.flatMap { i => list.map { j => (i, j) } }, Scala.::)

  @Test
  def arrayClosureParam(): Unit =
    eval("""list.map { i => intArray(0) + i }""", list.map { i => intArray(0) + i }, Scala.::)

  @Test
  def multipleGenricClosureParam(): Unit =
    eval("""multilist.map { i => multilist.map { j => (i,j) } }""", multilist.map { i => multilist.map { j => (i, j) } }, Scala.::)

  @Test
  def objectClosureParam(): Unit =
    eval("""list.map { _ + objectVal.value }""", list.map { _ + 11 }, Scala.::)

  @Test
  def primitiveClosureParam(): Unit =
    eval("""list.map { _ + int }""", list.map { _ + int }, Scala.::)

  @Test
  def anyValClosureParam(): Unit =
    eval("""list.map { i => anyVal.asInt() }""", list.map { i => "2" }, Scala.::)

  @Test
  def existentialTypeClosureParam(): Unit =
    eval("""Libs.existentialList.map { i => i.toString }""", List("1", "2"), Scala.::)

  @Ignore("TODO - O-8579 Support for nested lambdas over existential generics parameters")
  @Test
  def existentialGenricTypeClosureParam(): Unit =
    eval("""Libs.existentialMultilist.flatMap { i => i.map(_.toString) }""", List("1", "2"), Scala.::)

  @Ignore("TODO - O-8604 Toolbox bug with CanBuildFrom resolution")
  @Test
  def anyValClosureParamWithMethod(): Unit =
    eval("""list.map { i => anyVal.printMe }""", list.flatMap { i => "2" }, Scala.::)

  @Ignore("TODO - O-8578 - using values from objects in lambdas does not work")
  @Test
  def closureOnObjectValue(): Unit =
    eval("list.map(i => i + Libs.value)", "List(12, 13, 14)", Scala.::)
}

object LambdasTest extends BaseIntegrationTestCompanion
