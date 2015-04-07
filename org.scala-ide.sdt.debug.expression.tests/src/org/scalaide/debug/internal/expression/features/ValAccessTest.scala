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
  def testInt(): Unit = eval("int", 1, Java.primitives.int)

  @Test
  def testChar(): Unit = eval("char", 'c', Java.primitives.char)

  @Test
  def testDouble(): Unit = disableOnJava8 { eval("double", 1.1, Java.primitives.double) }

  @Test
  def testFloat(): Unit = eval("float", 1.1f, Java.primitives.float)

  @Test
  def testBoolean(): Unit = eval("boolean", false, Java.primitives.boolean)

  @Test
  def testString(): Unit = disableOnJava8 { eval("string", "Ala", Java.String) }

  @Test
  def testLong(): Unit = eval("long", 1L, Java.primitives.long)

  @Test
  def testStringMethod(): Unit = disableOnJava8 { eval("string.toLowerCase", "ala", Java.String) }

  @Test
  def testObjectList(): Unit = disableOnJava8 { eval("list", List("1", "2", "3"), Scala.::) }

  @Test
  def testObjectListMethod(): Unit = eval("list.mkString", "123", Java.String)

  @Test
  def testStrangeMethodsNamesMethod(): Unit = eval("*", 1, Java.primitives.int)

  @Test
  def testPlusOnVals(): Unit = eval("int + int", 2, Java.primitives.int)

  @Test
  def testOuterScopedVal(): Unit = eval("outer", "ala", Java.String)

  @Test
  def testLibClassVal(): Unit = eval("libClass", "LibClass(1)", "debug.LibClass")

  @Test
  def testObjectAccess(): Unit = eval("objectVal", "Libs - object", "debug.Libs$")

}

object ValAccessTest extends BaseIntegrationTestCompanion
