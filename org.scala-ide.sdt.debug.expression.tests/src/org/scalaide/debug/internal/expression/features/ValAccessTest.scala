/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala

class ValAccessTest extends BaseIntegrationTest(ValAccessTest) {

  @Test
  def testInt(): Unit = eval("int", "1", Java.boxed.Integer)

  @Test
  def testChar(): Unit = eval("char", "c", Java.boxed.Character)

  @Test
  def testDouble(): Unit = disableOnJava8 { eval("double", "1.1", Java.boxed.Double) }

  @Test
  def testFloat(): Unit = eval("float", "1.1", Java.boxed.Float)

  @Test
  def testBoolean(): Unit = eval("boolean", "false", Java.boxed.Boolean)

  @Test
  def testString(): Unit = disableOnJava8 { eval("string", "Ala", Java.boxed.String) }

  @Test
  def testLong(): Unit = eval("long", "1", Java.boxed.Long)

  @Test
  def testStringMethod(): Unit = disableOnJava8 { eval("string.toLowerCase", "ala", Java.boxed.String) }

  @Test
  def testObjectList(): Unit = disableOnJava8 { eval("list", "List(1, 2, 3)", Scala.::) }

  @Test
  def testObjectListMethod(): Unit = eval("list.mkString", "123", Java.boxed.String)

  @Test
  def testStrangeMethodsNamesMethod(): Unit = eval("*", "1", Java.boxed.Integer)

  @Test
  def testPlusOnVals(): Unit = eval("int + int", "2", Java.boxed.Integer)

  @Test
  def testOuterScopedVal(): Unit = eval("outer", "ala", Java.boxed.String)

  @Test
  def testLibClassVal(): Unit = eval("libClass", "LibClass(1)", "debug.LibClass")

  @Test
  def testObjectAccess(): Unit = eval("objectVal", "Libs - object", "debug.Libs$")

}

object ValAccessTest extends BaseIntegrationTestCompanion
