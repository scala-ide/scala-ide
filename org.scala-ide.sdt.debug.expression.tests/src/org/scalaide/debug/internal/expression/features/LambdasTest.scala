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
    eval(" ((x: Int) => x.toString.mkString)(2) ", "2", Java.String)

  @Test
  def higherOrderFunctionWithMultipleParameterLists(): Unit =
    eval("List(1, 2, 3).fold(0)(_ + _)", 6, Java.primitives.int)

  @Test
  def simpleLambdaWithWrittenTypes(): Unit =
    eval("list.map((_: Int) + 1)", list.map((_: Int) + 1), Scala.::)

  @Test
  def `function and primitives: list.filter(_ >= 2) `(): Unit =
    eval("list.filter(_ >= 2)", list.filter(_ >= 2), Scala.::)

  @Test
  def `function and primitives: list.filter(1 <) `(): Unit =
    eval("list.filter(1 <)", list.filter(1 <), Scala.::)

  @Test
  def `libClass.perform(_ + 2) `(): Unit =
    eval("libClass.perform(_ + 2)", 3, Java.primitives.int)

  @Test
  def `libClass.performByName(1 + 2) `(): Unit =
    eval("libClass.performByName(1 + 2)", 4, Java.primitives.int)

  @Test
  def mappingOnFullType(): Unit = {
    eval("multilist.map( (_: collection.immutable.List[Any]).toString)",
      multilist.map((_: collection.immutable.List[Any]).toString), Scala.::)
    eval("multilist.map( (list: collection.immutable.List[_]) => list.toString)",
      multilist.map((list: collection.immutable.List[_]) => list.toString), Scala.::)
  }

  @Test
  def `libClass.performByName("ala".mkString) `(): Unit =
    eval(""" libClass.performByNameGen("ala".mkString) """, "ala", Java.String)

  @Test
  def `lambda inside lambda over collection: multilist.map(list => list.map(_ + 1))`(): Unit =
    eval(""" multilist.map(list => list.map(_ + 1)) """, multilist.map(list => list.map(_ + 1)), Scala.::)

  @Test
  def objectTypedArgument(): Unit =
    eval(""" objectList.map(list => list.value) """, List(11, 11), Scala.::)

  @Test
  def genericTypedArgument(): Unit =
    eval(""" multilist.map(list => list.sum) """, multilist.map(list => list.sum), Scala.::)

  @Test
  def `lambda inside lambda inside lambda: multilist.map(list => list.map(_ + 1))`(): Unit =
    eval(""" multilist.map(list => list.map(int => list.map(_ + int).sum)) """,
        multilist.map(list => list.map(int => list.map(_ + int).sum)),
        Scala.::)

  @Test
  def `libClass.performOnList(list => list.map(_ + 1))`(): Unit =
    eval(""" libClass.performOnList(list => list.map(_ + 1)) """, List(2, 3), Scala.::)

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

  @Test
  def anyValClosureParamWithMethod(): Unit =
    eval("""list.map { i => anyVal.printMe }""", list.map { i => "2" }, Scala.::)

  @Test
  def closureOnObjectValue(): Unit =
    eval("list.map(i => i + Libs.value)", List(12, 13, 14), Scala.::)

  @Test
  def fullySpecifiedNameInLambda(): Unit =
    eval("list.map(i => i + scala.collection.immutable.List().size)", List(1, 2, 3), Scala.::)
}

object LambdasTest extends BaseIntegrationTestCompanion
