/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package proxies.phases

import scala.reflect.runtime.universe
import org.junit.Assert._
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class VariableProxiesTest extends HasEvaluator {

  private def testVariables(variables: String*)(in: String): Unit = {
    val code = Evaluator.parse(in)

    val phase = new SearchForUnboundVariables(Evaluator.toolbox, Set.empty)
    val foundVariables = phase.transform(TransformationPhaseData(code)).unboundVariables

    assertEquals(
      s"mismatch variables in: $in \n tree: ${universe.showRaw(code)}  \n",
      variables.toSet,
      foundVariables.map(_.name.toString))
  }

  @Test
  def with_variables(): Unit = testVariables("ala", "ola")("ala.apply(ola)")

  @Test
  def with_named_parameters() = testVariables("ala")("""ala.copy(name = "ola")""")

  @Test
  def with_assignment(): Unit = testVariables("ala", "ola")("""ala.name = ola""")

  @Test
  def with_chaining(): Unit = testVariables("ola", "ala")("""ala.Ala.name = ola""")

  @Test
  def with_variables_with_locals(): Unit = testVariables("ola")(
    """
      |val ala = "???"
      |ala.apply(ola)
      | """.stripMargin)

  @Test
  def with_anonymous_named_function(): Unit = testVariables("ola")(
    """
      |"ala".map {
      |  ala => ala.apply(ola)
      |}""".stripMargin)

  @Test
  def with_anonymous_underscore_function(): Unit = testVariables("ola")(
    """
      |"ala".map {
      |  _.apply(ola)
      |}""".stripMargin)

  @Test
  def with_anonymous_function_with_case(): Unit = testVariables("ola")(
    """
      |"ala".map {
      |  case ala => ala.apply(ola)
      |}""".stripMargin)

  @Test
  def with_variables_with_multiple_scopes(): Unit = {
    testVariables("ola", "ala")(
      """
        |"ala".map {
        | case List(ola: String) => ola
        | case ala => ala.apply(ola)
        |}
        |println(ala)""".stripMargin)

    testVariables("ola")(
      """
        |"ala".map {
        | case List(ola: String) => ola
        | case ala => ala.apply(ola)
        |}""".stripMargin)

    testVariables("ala", "ola")(
      """
        |"ala".map {
        | case ala => ala.apply(ola)
        |}
        |println(ala)
        | """.stripMargin)
  }

  @Test
  def with_for_comprehension(): Unit = testVariables("names")(
    """
      |for {
      | name <- names
      | initial = name.head
      |} yield initial
      | """.stripMargin)

  @Test
  def with_local_overloading_after(): Unit = testVariables("list", "y")(
    """
      |list.map{
      | x =>
      | val y = x.toList
      | y} + y
      | """.stripMargin)

  @Test
  def with_local_overloading_before(): Unit = testVariables("list", "y")(
    """
      |y + list.map{
      | x =>
      | val y = x.toList
      | y}
      | """.stripMargin)

  @Test
  def with_local_overloading_with_block(): Unit = testVariables("y")(
    """
      |{
      | val y = "ala"
      | y} + y
      | """.stripMargin)

  @Test
  def with_appy(): Unit = testVariables("x")(
    """
      |List(x, x, x).map(_.printX()).mkString
      | """.stripMargin)

  @Test
  def with_predef(): Unit = testVariables("x")(
    """
      |List(x).map(y => println(y)).mkString
      |Set(x)
      |Map(x -> x)
      |println(x)
      | """.stripMargin)

}
