/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.JavaBoxed
import org.scalaide.debug.internal.expression.ScalaOther

class ValAccessTest extends BaseIntegrationTest(ValAccessTest) {

  @Test
  def testInt(): Unit = eval("int", "1", JavaBoxed.Integer)

  @Test
  def testChar(): Unit = eval("char", "c", JavaBoxed.Character)

  @Test
  def testDouble(): Unit = eval("double", "1.1", JavaBoxed.Double)

  @Test
  def testFloat(): Unit = eval("float", "1.1", JavaBoxed.Float)

  @Test
  def testBoolean(): Unit = eval("boolean", "false", JavaBoxed.Boolean)

  @Test
  def testString(): Unit = eval("string", "Ala", JavaBoxed.String)

  @Test
  def testLong(): Unit = eval("long", "1", JavaBoxed.Long)

  @Test
  def testStringMethod(): Unit = eval("string.toLowerCase", "ala", JavaBoxed.String)

  @Test
  def testObjectList(): Unit = eval("list", "List(1, 2, 3)", ScalaOther.scalaList)

  @Test
  def testObjectListMethod(): Unit = eval("list.mkString", "123", JavaBoxed.String)

  @Test
  def testStrangeMethodsNamesMethod(): Unit = eval("*", "1", JavaBoxed.Integer)

  @Test
  def testPlusOnVals(): Unit = eval("int + int", "2", JavaBoxed.Integer)

  @Test
  def testOuterScopedVal(): Unit = eval("outer", "ala", JavaBoxed.String)

  @Test
  def testLibClassVal(): Unit = eval("libClass", "LibClass(1)", "debug.LibClass")

  @Test
  def testObjectAccess(): Unit = eval("objectVal", "Libs - object", "debug.Libs$")

}

object ValAccessTest extends BaseIntegrationTestCompanion