/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TestValues.JavaTestCase
import org.scalaide.debug.internal.expression.TestValues.JavaTestCase._

class JavaNonStaticFieldsAndMethodsTest extends BaseIntegrationTest(JavaNonStaticFieldsAndMethodsTest) {

  @Test
  def getValuesOfFields(): Unit = {
    eval("javaLibClass.normalInt", JavaLibClass.normalInt, Java.primitives.int)
    eval("javaLibClass.normalString", JavaLibClass.normalString, Java.String)
    eval("javaLibClass.self", JavaLibClass.asString, "debug.JavaLibClass")
    eval("javaLibClass.normalNull", null, Scala.nullType)
  }

  @Test
  def checkAdditionalOperationsOnRetrievedStaticFields(): Unit = {
    eval("javaLibClass.self.normalInt", JavaLibClass.normalInt, Java.primitives.int) // this one works because self is not AnyVal
    eval("javaLibClass.normalInt - 2.0", JavaLibClass.normalInt - 2.0, Java.primitives.double)
    eval("javaLibClass.normalInt + 2.0", JavaLibClass.normalInt + 2.0, Java.primitives.double)
    eval("javaLibClass.self.normalInt", JavaLibClass.normalInt, Java.primitives.int)
    eval("""javaLibClass.normalString + "foo" """, JavaLibClass.normalString + "foo", Java.String)
  }

  @Test
  def checkEqualityOfFields(): Unit = {
    eval(s"""javaLibClass.normalString == "${JavaLibClass.normalString}" """, true, Java.primitives.boolean)
    eval("javaLibClass.normalString == 15", false, Java.primitives.boolean)
    eval(s"""javaLibClass.normalString != "${JavaLibClass.normalString}" """, false, Java.primitives.boolean)
    eval("javaLibClass.normalString != 15", true, Java.primitives.boolean)
  }

  @Test
  def changeValuesOfFields(): Unit = {
    eval("""javaLibClass.normalStringToChange = "tesseract"; javaLibClass.normalStringToChange""", "tesseract", Java.String)
  }

  @Ignore("Fails when running whole test suite with https://issues.scala-lang.org/browse/SI-9218")
  @Test
  def invokeGenericMethods(): Unit = {
    eval("javaLibClass.genericMethod(false)", false, Java.primitives.boolean)
    eval("javaLibClass.genericMethod('a')", 'a', Java.boxed.Character)
  }

  @Test
  def invokeVarArgsMethods(): Unit = {
    eval("javaLibClass.varArgMethod()", Array(), Scala.Array(Scala.primitives.Int))
    eval("javaLibClass.varArgMethod(1)", Array(1), Scala.Array(Scala.primitives.Int))
    eval("javaLibClass.varArgMethod(1, 2)", Array(1, 2), Scala.Array(Scala.primitives.Int))
    eval("javaLibClass.varArgMethod(1, 2, 3)", Array(1, 2, 3), Scala.Array(Scala.primitives.Int))
  }

  @Ignore("Fails when running whole test suite with https://issues.scala-lang.org/browse/SI-9218")
  @Test
  def invokeGenericVarArgsMethod(): Unit =
    eval("""javaLibClass.varArgGenericMethod[String]("1.0", "2.0", "3.0")""", Array(1.0, 2.0, 3.0), Scala.Array(Java.Object))
}

object JavaNonStaticFieldsAndMethodsTest extends BaseIntegrationTestCompanion(JavaTestCase) with DefaultBeforeAfterAll
