/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases

import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TypesContext

@RunWith(classOf[JUnit4])
class TypeExtractionTest extends HasEvaluator {

  def testTypes(code: String, resultType: String) {
    val toolbox = Evaluator.toolbox
    val compiled = toolbox.typecheck(Evaluator.parse(code))
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
    testTypes(""" "123L" """, Java.boxed.String)
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
    testTypes("""  val i = ("ala", 1); i._1 """, Java.boxed.String)
  }
}