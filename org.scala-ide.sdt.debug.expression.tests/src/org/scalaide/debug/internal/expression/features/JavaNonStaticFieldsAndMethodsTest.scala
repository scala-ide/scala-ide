/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TestValues.any2String
import org.scalaide.debug.internal.expression.TestValues.JavaTestCase
import JavaTestCase._

class JavaNonStaticFieldsAndMethodsTest extends BaseIntegrationTest(JavaNonStaticFieldsAndMethodsTest) {

  @Test
  def getValuesOfFields(): Unit = {
    eval("javaLibClass.normalInt", JavaLibClass.normalInt, Java.boxed.Integer)
    eval("javaLibClass.normalString", JavaLibClass.normalString, Java.boxed.String)
    eval("javaLibClass.self", JavaLibClass.asString, "debug.JavaLibClass")
    eval("javaLibClass.normalNull", Scala.nullLiteral, Scala.nullType)
  }

  @Test
  def checkAdditionalOperationsOnRetrievedStaticFields(): Unit = {
    eval("javaLibClass.self.normalInt", JavaLibClass.normalInt, Java.boxed.Integer) // this one works because self is not AnyVal
    eval("javaLibClass.normalInt - 2.0", JavaLibClass.normalInt - 2.0, Java.boxed.Double)
    eval("javaLibClass.normalInt + 2.0", JavaLibClass.normalInt + 2.0, Java.boxed.Double)
    eval("javaLibClass.self.normalInt", JavaLibClass.normalInt, Java.boxed.Integer)
    eval("""javaLibClass.normalString + "foo" """, JavaLibClass.normalString + "foo", Java.boxed.String)
  }

  @Test
  def checkEqualityOfFields(): Unit = {
    eval(s"""javaLibClass.normalString == "${JavaLibClass.normalString}" """, true, Java.boxed.Boolean)
    eval("javaLibClass.normalString == 15", false, Java.boxed.Boolean)
    eval(s"""javaLibClass.normalString != "${JavaLibClass.normalString}" """, false, Java.boxed.Boolean)
    eval("javaLibClass.normalString != 15", true, Java.boxed.Boolean)
  }

  @Test
  def changeValuesOfFields(): Unit = {
    eval("""javaLibClass.normalStringToChange = "tesseract"; javaLibClass.normalStringToChange""", "tesseract", Java.boxed.String)
  }

  // TODO this should be enabled or logged as an issue
  @Ignore("Fails when running whole test suite - java.lang.IndexOutOfBoundsException: 0 from scala.reflect.internal.Importers$StandardImporter.recreateOrRelink$1")
  @Test
  def invokeGenericMethods(): Unit = {
    eval("javaLibClass.genericMethod(false)", false, Java.boxed.Boolean)
    eval("javaLibClass.genericMethod('a')", 'a', Java.boxed.Character)
  }

  @Test
  def invokeVarArgsMethods(): Unit = {
    eval("javaLibClass.varArgMethod()", "Array()", Scala.Array(Scala.primitives.Int))
    eval("javaLibClass.varArgMethod(1)", "Array(1)", Scala.Array(Scala.primitives.Int))
    eval("javaLibClass.varArgMethod(1, 2)", "Array(1, 2)", Scala.Array(Scala.primitives.Int))
    eval("javaLibClass.varArgMethod(1, 2, 3)", "Array(1, 2, 3)", Scala.Array(Scala.primitives.Int))
  }

  // TODO this should be enabled or logged as an issue
  @Ignore("Fails when running whole test suite - java.lang.IndexOutOfBoundsException: 0 from scala.reflect.internal.Importers$StandardImporter.recreateOrRelink$1")
  @Test
  def invokeGenericVarArgsMethod(): Unit =
    eval("javaLibClass.varArgGenericMethod[java.lang.Double](1.0, 2.0, 3.0)", "Array(1.0, 2.0, 3.0)", Scala.Array(Java.`object`))
}

object JavaNonStaticFieldsAndMethodsTest extends BaseIntegrationTestCompanion(JavaTestCase)
