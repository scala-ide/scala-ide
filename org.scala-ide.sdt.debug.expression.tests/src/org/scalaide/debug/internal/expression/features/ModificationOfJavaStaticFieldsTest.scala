/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.DefaultBeforeAfterAll
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TestValues.JavaTestCase

class ModificationOfJavaStaticFieldsTest extends BaseIntegrationTest(ModificationOfJavaStaticFieldsTest) {

  @Test
  def changeStaticFieldsOfClass(): Unit = {
    eval("JavaLibClass.staticString = Int.MaxValue.toString; JavaLibClass.staticString", Int.MaxValue, Java.String)
    eval("JavaLibClass.staticInt -= 2 + 2; JavaLibClass.staticInt", JavaTestCase.JavaLibClass.staticInt - 4, Java.primitives.int)
  }

  @Test
  def changeStaticFieldsOfInnerStaticClass(): Unit = {
    eval("""JavaLibClass.InnerStaticClass.staticString = "bar"; JavaLibClass.InnerStaticClass.staticString""", "bar", Java.String)
    eval("""JavaLibClass.InnerStaticClass.staticString = "baz" + JavaLibClass.InnerStaticClass.staticString; JavaLibClass.InnerStaticClass.staticString""",
      "bazbar", Java.String)
    eval("JavaLibClass.InnerStaticClass.innerStaticDouble = -42; JavaLibClass.InnerStaticClass.innerStaticDouble", -42.0, Java.primitives.double)
  }

  @Test
  def changeStaticFieldOfInnerStaticClassOfInnerStaticClass(): Unit = {
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt = 123; JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt",
      123, Java.primitives.int)
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt = -110", (), Scala.unitType)
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt", -110, Java.primitives.int)
  }
}

object ModificationOfJavaStaticFieldsTest extends BaseIntegrationTestCompanion(JavaTestCase) with DefaultBeforeAfterAll
