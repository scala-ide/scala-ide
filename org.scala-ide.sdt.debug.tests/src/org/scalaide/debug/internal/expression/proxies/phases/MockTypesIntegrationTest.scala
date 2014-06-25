/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.proxies.phases

import org.junit.Assert._
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
class MockTypesIntegrationTest extends BaseProxyTest {

  def testCode(code: String)(variables: MockVariable*)(expected: String): Unit =
    baseTest(code, variables = variables.toSet) {
      case (context, proxy) => assertEquals("Result differs", context.show(proxy), expected)
    }

  @Test
  def testForList_mkString(): Unit =
    testCode("list.mkString") {
      MockVariable("list", "scala.collection.immutable.List", MockCall("mkString", StringMock("123"), ""))
    }("123")

  @Test
  def testForList_get(): Unit =
    testCode("list(1)") {
      MockVariable("list", "scala.collection.immutable.List", MockCall("apply", StringMock("123"), ""))
    }("123")

  @Test
  def testForDynamics(): Unit =
    testCode("list(1).toInt") {
      MockVariable("list", "scala.collection.immutable.List", MockCall("apply",
        MockVariable("", "", MockCall("toInt", StringMock("123")))))
    }("123")
}
