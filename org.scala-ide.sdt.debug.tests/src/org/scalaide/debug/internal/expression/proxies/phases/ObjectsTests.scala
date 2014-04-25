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
import org.scalaide.debug.internal.expression.context.JdiContext

@RunWith(classOf[JUnit4])
class ObjectsTests extends BaseProxyTest {

  private def testObjects(code: String, objectsPre: MockVariable*)(result: String) {
    def createObjectMock(v: MockVariable): MockVariable =
      MockVariable(name = JdiContext.toObject(v.name), v.tpe, v.calls: _*)

    val objects = objectsPre.map(createObjectMock)

    baseTest(code, objects = objects.toSet) {
      case (context, proxy) =>
        assertEquals("found objects mismatch",
          objects.map(_.name).toSet,
          context.calledObject.map(_.name))

        assertEquals("results mismatch",
          result,
          context.show(proxy))
    }
  }

  @Test
  def objectApplyTest(): Unit = {
    testObjects("scala.collection.immutable.List(1)",
      MockVariable("scala.collection.immutable.List", "", MockCall("apply", StringMock("List(1)"))))("List(1)")
  }

  @Test
  def simpleObjectTest(): Unit = {
    testObjects("scala.collection.immutable.List",
      MockVariable("scala.collection.immutable.List", ""))(JdiContext.toObject("scala.collection.immutable.List"))
  }

  @Test
  def methodObjTest(): Unit = {
    testObjects("scala.collection.immutable.List.empty",
      MockVariable("scala.collection.immutable.List", "", MockCall("empty", StringMock("List()"))))("List()")
  }

  //with predef

  @Test
  def objectApplyTestWithPredef(): Unit = {
    testObjects("List(1)",
      MockVariable("scala.collection.immutable.List", "", MockCall("apply", StringMock("List(1)"))))("List(1)")
  }

  @Test
  def simpleObjectTestWithPredef(): Unit = {
    testObjects("List",
      MockVariable("scala.collection.immutable.List", ""))(JdiContext.toObject("scala.collection.immutable.List"))
  }

  @Test
  def methodObjTestWithPredef(): Unit = {
    testObjects("List.empty",
      MockVariable("scala.collection.immutable.List", "", MockCall("empty", StringMock("List()"))))("List()")
  }

}
