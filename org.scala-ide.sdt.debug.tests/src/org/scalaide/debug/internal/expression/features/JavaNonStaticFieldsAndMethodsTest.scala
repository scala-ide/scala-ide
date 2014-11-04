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
import org.scalaide.debug.internal.expression.TestValues.ValuesTestCase._

class JavaNonStaticFieldsAndMethodsTest extends BaseIntegrationTest(JavaNonStaticFieldsAndMethodsTest) {

  @Test
  def getValuesOfFields(): Unit = {
    eval("javaLibClass.normalInt", JavaLibClass.normalInt, Java.boxed.Integer)
    eval("javaLibClass.normalString", JavaLibClass.normalString, Java.boxed.String)
    eval("javaLibClass.self", JavaLibClass.asString, "debug.JavaLibClass")
    eval("javaLibClass.normalNull", Scala.nullLiteral, Scala.nullType)
  }

  @Ignore("TODO O-7263 Fails as it doesn't see methods of field's value - generating stubs doesn't support local operations on Java fields of type AnyVal")
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

  // see description in ModificationOfJavaStaticFieldsTest for error details
  @Ignore("TODO O-7264 support for assignment to Java fields")
  @Test
  def changeValuesOfFields(): Unit = {
    eval("""javaLibClass.normalStringToChange = "tesseract"; javaLibClass.normalStringToChange""", "tesseract", Java.boxed.String)
  }

  @Ignore("Fails when running whole test suite - java.lang.IndexOutOfBoundsException: 0 from scala.reflect.internal.Importers$StandardImporter.recreateOrRelink$1")
  @Test
  def invokeMethods(): Unit = {
    eval("javaLibClass.genericMethod(false)", false, Java.boxed.Boolean)
    eval("javaLibClass.genericMethod('a')", 'a', Java.boxed.Character)
  }
}

object JavaNonStaticFieldsAndMethodsTest extends BaseIntegrationTestCompanion
