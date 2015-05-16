/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import Names.Java

@RunWith(classOf[JUnit4])
class TypeExtractionTest extends HasEvaluator {

  def testTypes(code: String, resultType: String) {
    val toolbox = Evaluator.toolbox
    val compiled = toolbox.typecheck(Evaluator.parse(code))
    val extracted = TypeNames.getFromTree(compiled)
    assertEquals(s"mismatch type in: $code type( ${compiled.tpe})", resultType, extracted)
  }

  @Test
  def predef(): Unit = {
    testTypes("123", "Int")
    testTypes("1.23", "Double")
    testTypes("1.23f", "Float")
    testTypes("123L", "Long")
    testTypes(""" 'c' """, "Char")
    testTypes(""" "123L" """, Java.String)
  }

  @Test
  def generics(): Unit = {
    testTypes("Set(1, 2, 3)", "scala.collection.immutable.Set[Int]")
    testTypes("""  "ala".map(_.toInt).sum """, "Int")
  }

  @Test
  def fields(): Unit = {
    testTypes("val set = Set(1, 2,3); set", "scala.collection.immutable.Set[Int]")
    testTypes("""  val i = "ala".map(_.toInt).sum; i + 1 """, "Int")
    testTypes("""  val i = ("ala", 1); i._1 """, Java.String)
  }
}
