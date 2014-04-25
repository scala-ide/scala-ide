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
import org.scalaide.debug.internal.expression.FunctionStub
import org.scalaide.debug.internal.expression.TypesContext
import org.scalaide.debug.internal.expression._
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.logging.HasLogger

@RunWith(classOf[JUnit4])
class TypeSearchMockTest {

  protected object Evaluator extends ExpressionEvaluator(getClass.getClassLoader)

  private def testMocks(code: String, results: Map[String, Set[FunctionStub]]) {
    val context = new TypesContext
    val transformer = TypeSearch(Evaluator.toolbox, context)

    val parsed = Evaluator.parse(code)
    val typeChecked = Evaluator.toolbox.typeCheck(parsed)
    transformer.transform(typeChecked)

    val types = context.stubs.filterNot {
      case (_, functionStubs) => functionStubs.isEmpty
    }

    assertEquals(s"Found types are incorrect in: $code", types, results)
  }

  private val listType = "scala.collection.immutable.List"
  private val intType = "scala.Int"
  private val booleanType = "scala.Boolean"

  private def testWithListCode(codeLine: String, listGenericType: String = "Int")(stubs: (String, Set[FunctionStub])*) = {
    testMocks(
      s"""var list: List[$listGenericType] = null
        ${codeLine}""", stubs.toMap)
  }

  private def testOnList(codeLine: String, listGenericType: String = "Int")(functions: FunctionStub*) =
    testMocks(
      s"""
        var list: List[$listGenericType] = null
        ${codeLine}
      """, Map(listType -> functions.toSet))

  //tests

  @Test
  def function_only_parameter_list(): Unit = {
    testOnList("list.apply(1)") {
      FunctionStub("apply", Some(intType), Seq(Seq(intType)))
    }
  }

  @Test
  def function_only_name(): Unit = {
    testOnList("list.size") {
      FunctionStub("size", Some(intType))
    }
  }

  @Test
  def function_only_parameter_list_with_apply(): Unit = {
    testOnList("list(1)") {
      FunctionStub("apply", Some(intType), Seq(Seq(intType)))
    }
  }

  @Test
  def function_with_typeApply_and_parameters(): Unit = {
    testOnList(
      """var b: List[Int] = null
        |list.endsWith(b)
        |""".stripMargin) {
        FunctionStub("endsWith", Some(booleanType), Seq(Seq(listType)))
      }
  }

  @Test
  def function_with_typeApply(): Unit = {
    testOnList("list.sum") {
      FunctionStub("sum", Some(intType), implicitArgumentTypes = Seq(JdiContext.toObject("scala.math.Numeric.IntIsIntegral")))
    }
  }

  @Test
  def function_with_typeApply_parameter_and_implicit(): Unit = {
    testWithListCode("list.map(_ + 1)")(
      (listType, Set(FunctionStub("map", Some(listType), Seq(Seq("scala.Function1")), Seq("scala.collection.generic.CanBuildFrom")))),
      (JdiContext.toObject(listType), Set(FunctionStub("canBuildFrom", Some("scala.collection.generic.CanBuildFrom"), List(), List()))),
      (intType -> Set(FunctionStub("+", Some(intType), Seq(Seq(intType))))))
  }
}