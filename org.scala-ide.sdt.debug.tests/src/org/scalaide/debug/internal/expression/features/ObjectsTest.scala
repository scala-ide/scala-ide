/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.ScalaOther

class ObjectsTest extends BaseIntegrationTest(ObjectsTest) {

  @Test
  def testListApply(): Unit = eval("List(1,2)", "List(1, 2)", ScalaOther.scalaList)

  @Test
  def testListApplyWithMkString(): Unit = eval("List(1,2).mkString", "12", JavaBoxed.String)

}

object ObjectsTest extends BaseIntegrationTestCompanion