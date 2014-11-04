/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.features

import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.Names.Scala
import org.scalaide.debug.internal.expression.TestValues.ValuesTestCase.JavaLibClass
import org.scalaide.debug.internal.expression.TestValues.any2String
import org.junit.Ignore

// such tests fail due to java.lang.UnsupportedOperationException: Position.end on NoPosition from scala.reflect.internal.util.Position.fail
// from typer during the compilation using Toolbox
class ModificationOfJavaStaticFieldsTest extends BaseIntegrationTest(ModificationOfJavaStaticFieldsTest) {

  @Ignore("TODO O-7264 support for assignment to Java fields")
  @Test
  def changeStaticFieldsOfClass(): Unit = {
    eval("JavaLibClass.staticString = Int.MaxValue.toString; JavaLibClass.staticString", Int.MaxValue, Java.boxed.String)
    eval("JavaLibClass.staticInt -= 2 + 2; JavaLibClass.staticInt", JavaLibClass.staticInt - 4, Java.boxed.Integer)
  }

  @Ignore("TODO O-7264 support for assignment to Java fields")
  @Test
  def changeStaticFieldsOfInnerStaticClass(): Unit = {
    eval("""JavaLibClass.InnerStaticClass.staticString = "bar"; JavaLibClass.InnerStaticClass.staticString""", "bar", Java.boxed.String)
    eval("""JavaLibClass.InnerStaticClass.staticString = "baz" + JavaLibClass.InnerStaticClass.staticString; JavaLibClass.InnerStaticClass.staticString""",
      "bazbar", Java.boxed.String)
    eval("JavaLibClass.InnerStaticClass.innerStaticDouble = -42; JavaLibClass.InnerStaticClass.innerStaticDouble",
      -42, Java.boxed.Integer)
  }

  @Ignore("TODO O-7264 support for assignment to Java fields")
  @Test
  def changeStaticFieldOfInnerStaticClassOfInnerStaticClass(): Unit = {
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt = 123; JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt",
      123, Java.boxed.Integer)
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt = -110", Scala.unitLiteral, Scala.unitType)
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt", -110, Java.boxed.Integer)
  }
}

object ModificationOfJavaStaticFieldsTest extends BaseIntegrationTestCompanion
