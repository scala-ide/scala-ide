/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.TypesContext
import scala.tools.reflect.ToolBox
import scala.reflect.runtime.universe
import org.scalaide.debug.internal.expression.context.JdiContext

@RunWith(classOf[JUnit4])
class TypeExtractionTest extends HasEvaluator {

  def testTypes(code: String, resultType: String) {
    val toolbox = Evaluator.toolbox
    val compiled = toolbox.typeCheck(Evaluator.parse(code))
    val extracted = new TypesContext().treeTypeName(compiled)
    assertEquals(s"mismatch type in: $code type( ${compiled.tpe})", Some(resultType), extracted)
  }

  @Test
  def predef(): Unit = {
    testTypes("123", "scala.Int")
    testTypes("1.23", "scala.Double")
    testTypes("1.23f", "scala.Float")
    testTypes("123L", "scala.Long")
    testTypes(""" 'c' """, "scala.Char")
    testTypes(""" "123L" """, JavaBoxed.String)
  }

  @Test
  def generics(): Unit = {
    testTypes("Set(1, 2, 3)", "scala.collection.immutable.Set")
    testTypes("""  "ala".map(_.toInt).sum """, "scala.Int")
  }

  @Test
  def fields(): Unit = {
    testTypes("val set = Set(1, 2,3); set", "scala.collection.immutable.Set")
    testTypes("""  val i = "ala".map(_.toInt).sum; i + 1 """, "scala.Int")
    testTypes("""  val i = ("ala", 1); i._1 """, JavaBoxed.String)
  }
}