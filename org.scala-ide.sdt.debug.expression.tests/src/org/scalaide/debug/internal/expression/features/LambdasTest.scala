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
    eval("list.map((_: Int) + 1)", "List(2, 3, 4)", Scala.::)
  }

  @Test
  def `function and primitives: list.filter(_ >= 2) `(): Unit = disableOnJava8 {
    eval("list.filter(_ >= 2)", "List(2, 3)", Scala.::)
  }

  @Test
  def `function and primitives: list.filter(1 <) `(): Unit = disableOnJava8 {
    eval("list.filter(1 <)", "List(2, 3)", Scala.::)
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
    eval("multilist.map( (_: collection.immutable.List[Any]).toString)", "List(List(1), List(2, 3))", Scala.::)
    eval("multilist.map( (list: collection.immutable.List[_]) => list.toString)", "List(List(1), List(2, 3))", Scala.::)
  }

  @Test
  def `libClass.performByName("ala".mkString) `(): Unit =
    eval(""" libClass.performByNameGen("ala".mkString) """, "ala", Java.boxed.String)

  @Test
  def `lambda inside lambda over collection: multilist.map(list => list.map(_ + 1))`(): Unit =
    eval(""" multilist.map(list => list.map(_ + 1)) """, "List(List(2), List(3, 4))", Scala.::)

  @Test
  def objectTypedArgument(): Unit =
    eval(""" objectList.map(list => list.value) """, "List(11, 11)", Scala.::)

  @Test
  def genericTypedArgument(): Unit =
    eval(""" multilist.map(list => list.sum) """, "List(1, 5)", Scala.::)

  @Test
  def `lambda inside lambda inside lambda: multilist.map(list => list.map(_ + 1))`(): Unit =
    eval(""" multilist.map(list => list.map(int => list.map(_ + int).sum)) """, "List(List(2), List(9, 11))", Scala.::)

  @Ignore("TODO - O-8498 - nested lambdas closing over generic type")
  @Test
  def nestedLambdaInCarthsianProduct(): Unit =
    eval("""list.flatMap { i => list.map { j => (i,j) } }""", list.flatMap { i => list.map { j => (i, j) } }, Scala.::)

  @Test
  def `libClass.performOnList(list => list.map(_ + 1))`(): Unit =
    eval(""" libClass.performOnList(list => list.map(_ + 1)) """, "List(2, 3)", Scala.::)
}

object LambdasTest extends BaseIntegrationTestCompanion
