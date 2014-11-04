/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.mocks

import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.scalaide.debug.internal.expression.MethodStub
import org.scalaide.debug.internal.expression.TypesContext
import org.scalaide.debug.internal.expression.context.JdiContext
import org.scalaide.debug.internal.expression.proxies.phases.HasEvaluator
import org.scalaide.debug.internal.expression.proxies.phases.TypeSearch

@RunWith(classOf[JUnit4])
class TypeSearchMockTest extends HasEvaluator {

  private def testMocks(code: String, results: Map[String, Set[MethodStub]]) {
    val context = new TypesContext
    val transformer = TypeSearch(Evaluator.toolbox, context)

    val parsed = Evaluator.parse(code)
    val typeChecked = Evaluator.toolbox.typecheck(parsed)
    transformer.transform(typeChecked)

    val types = context.stubs.filterNot {
      case (_, methodStubs) => methodStubs.isEmpty
    }

    assertEquals(s"Found types are incorrect in: $code", types, results)
  }

  private val listType = "scala.collection.immutable.List"
  private val intType = "scala.Int"
  private val booleanType = "scala.Boolean"

  private def testWithListCode(codeLine: String, listGenericType: String = "Int")(stubs: (String, Set[MethodStub])*) =
    testMocks(
      s"""var list: List[$listGenericType] = null
        ${codeLine}""", stubs.toMap)

  private def testOnList(codeLine: String, listGenericType: String = "Int")(functions: MethodStub*) =
    testMocks(
      s"""
        var list: List[$listGenericType] = null
        ${codeLine}
      """, Map(listType -> functions.toSet))

  private val listTypeScala = "scala.package$List"

  //tests

  @Test
  def function_only_parameter_list(): Unit =
    testOnList("list.apply(1)") {
      MethodStub("apply", listTypeScala, Some(intType), Seq(Seq(intType)))
    }

  @Test
  def function_only_name(): Unit =
    testOnList("list.size") {
      MethodStub("size", listTypeScala, Some(intType))
    }

  @Test
  def function_only_parameter_list_with_apply(): Unit =
    testOnList("list(1)") {
      MethodStub("apply", listTypeScala, Some(intType), Seq(Seq(intType)))
    }

  @Test
  def function_with_typeApply_and_parameters(): Unit =
    testOnList(
      """var b: List[Int] = null
        |list.endsWith(b)
        | """.stripMargin) {
        MethodStub("endsWith", listTypeScala, Some(booleanType), Seq(Seq(listType)))
      }

  @Test
  def function_with_typeApply(): Unit =
    testOnList("list.sum") {
      MethodStub("sum", listTypeScala, Some(intType), paramTypes = Seq(Seq(JdiContext.toObjectOrStaticCall("scala.math.Numeric.IntIsIntegral"))))
    }

  @Test
  def function_with_typeApply_parameter_and_implicit(): Unit = {
    val cbf = "scala.collection.generic.CanBuildFrom"
    testWithListCode("list.map(_ + 1)")(
      (listType, Set(MethodStub("map", listTypeScala, Some(listType), Seq(Seq("scala.Function1"), Seq(cbf))))),
      (JdiContext.toObjectOrStaticCall(listType), Set(MethodStub("canBuildFrom", listType, Some(cbf), Nil))),
      (intType -> Set(MethodStub("+", "scala.Int", Some(intType), Seq(Seq(intType))))))
  }
}