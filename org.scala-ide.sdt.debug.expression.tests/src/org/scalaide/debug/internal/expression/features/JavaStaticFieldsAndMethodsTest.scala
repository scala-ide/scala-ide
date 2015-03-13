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
import org.scalaide.debug.internal.expression.TestValues.JavaTestCase._

class JavaStaticFieldsAndMethodsTest extends BaseIntegrationTest(JavaStaticFieldsAndMethodsTest) {

  @Test
  def getValuesOfStaticFieldsOfClass(): Unit = {
    eval("JavaLibClass.staticString", JavaLibClass.staticString, Java.boxed.String)
    eval("JavaLibClass.staticInt", JavaLibClass.staticInt, Java.boxed.Integer)
    eval("JavaLibClass.staticNull", Scala.nullLiteral, Scala.nullType)
  }

  @Test
  def checkEqualityOfStaticFieldsOfClass(): Unit = {
    eval(s"""JavaLibClass.staticString == "${JavaLibClass.staticString}" """, true, Java.boxed.Boolean)
    eval(s"""JavaLibClass.staticString == "${JavaLibClass.staticString}42" """, false, Java.boxed.Boolean)
    eval(s"""JavaLibClass.staticString != "${JavaLibClass.staticString}" """, false, Java.boxed.Boolean)
    eval(s"""JavaLibClass.staticString != "${JavaLibClass.staticString}42" """, true, Java.boxed.Boolean)

    eval("JavaLibClass.staticInt == JavaLibClass.staticInt", true, Java.boxed.Boolean)
    eval("JavaLibClass.staticInt == 2 + 2", false, Java.boxed.Boolean)
    eval("JavaLibClass.staticInt != JavaLibClass.staticInt", false, Java.boxed.Boolean)
    eval("JavaLibClass.staticInt != 2 + 2", true, Java.boxed.Boolean)
  }

  @Test
  def invokeStaticMethodsOfClass(): Unit = {
    eval("""JavaLibClass.staticStringMethod("foo")""", JavaLibClass.staticStringMethod("foo"), Java.boxed.String)
    eval("JavaLibClass.staticIntMethod", JavaLibClass.staticIntMethod, Java.boxed.Integer)
  }

  @Test
  def invokeStaticGenericMethodsOfClass(): Unit = {
    eval("JavaLibClass.staticGenericMethod[Double](7.1)", 7.1, Java.boxed.Double)
    eval("JavaLibClass.staticGenericMethod('g')", 'g', Java.boxed.Character)
  }

  @Test
  def invokeStaticVarArgsMethodsOfClass(): Unit = {
    eval("JavaLibClass.staticVarArgMethod(1, 2, 3)", "Array(1, 2, 3)", Scala.Array(Scala.primitives.Int))
    eval("JavaLibClass.staticGenericVarArgMethod[java.lang.Double](1, 2, 3)", "Array(1.0, 2.0, 3.0)", Scala.Array(Java.Object))
  }

  @Test
  def classStaticFieldsIntegration(): Unit = {
    eval("JavaLibClass.staticInt - 2.0", JavaLibClass.staticInt - 2.0, Java.boxed.Double)
    eval("JavaLibClass.staticInt + 2.0", JavaLibClass.staticInt + 2.0, Java.boxed.Double)
    eval("""JavaLibClass.staticString + "foo" """, JavaLibClass.staticString + "foo", Java.boxed.String)
  }

  @Test
  def classStaticMethodsIntegration(): Unit = {
    eval("""JavaLibClass.staticStringMethod("foo") + 42""", JavaLibClass.staticStringMethod("foo") + 42, Java.boxed.String)
    eval("JavaLibClass.staticIntMethod - 42", JavaLibClass.staticIntMethod - 42, Java.boxed.Integer)
    eval("JavaLibClass.staticIntMethod + 42", JavaLibClass.staticIntMethod + 42, Java.boxed.Integer)
  }

  @Test
  def getValuesOfStaticFieldsOfInnerStaticClass(): Unit = {
    eval("JavaLibClass.InnerStaticClass.staticString", JavaLibClass.InnerStaticClass.staticString, Java.boxed.String)
    eval("JavaLibClass.InnerStaticClass.innerStaticDouble", JavaLibClass.InnerStaticClass.innerStaticDouble, Java.boxed.Double)
    eval("JavaLibClass.InnerStaticClass.staticInstanceOfOuterClass", JavaLibClass.asString, "debug.JavaLibClass")
  }

  @Test
  def checkEqualityOfStaticFieldsOfInnerStaticClass(): Unit = {
    eval("JavaLibClass.InnerStaticClass.staticInstanceOfOuterClass == JavaLibClass.InnerStaticClass.staticInstanceOfOuterClass",
      true, Java.boxed.Boolean)
    eval("""JavaLibClass.InnerStaticClass.staticInstanceOfOuterClass == "foo" """, false, Java.boxed.Boolean)
    eval("JavaLibClass.InnerStaticClass.staticInstanceOfOuterClass != JavaLibClass.InnerStaticClass.staticInstanceOfOuterClass",
      false, Java.boxed.Boolean)
    eval("""JavaLibClass.InnerStaticClass.staticInstanceOfOuterClass != "foo" """, true, Java.boxed.Boolean)
  }

  @Test
  def invokeStaticMethodOfInnerStaticClass(): Unit = {
    eval("""JavaLibClass.InnerStaticClass.innerStaticMethod("foo")""",
      JavaLibClass.InnerStaticClass.innerStaticMethod("foo"),
      Java.boxed.String)
    eval("JavaLibClass.InnerStaticClass.innerStaticGenericMethod(42)", 42, Java.boxed.Integer)
    eval("JavaLibClass.InnerStaticClass.innerStaticGenericMethod(false)", false, Java.boxed.Boolean)
  }

  // TODO this test depends on location of a test class in a test suite
  // for different ordering sometimes the type couldn't be loaded when running a full test suite - despite attempts to solve this
  // it's related also to a few other tests in this class checking so many levels of nesting
  @Test
  def getValueOfStaticFieldOfInnerStaticClassOfInnerStaticClass(): Unit = {
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt",
      JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt,
      Java.boxed.Integer)
  }

  @Test
  def checkEqualityOfStaticFieldOfInnerStaticClassOfInnerStaticClass(): Unit = {
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt == JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt",
      true, Java.boxed.Boolean)
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt == 42",
      false, Java.boxed.Boolean)
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt != JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt",
      false, Java.boxed.Boolean)
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt != 42",
      true, Java.boxed.Boolean)
  }

  @Test
  def invokeStaticMethodOfInnerStaticClassOfInnerStaticClass(): Unit = {
    eval("""JavaLibClass.InnerStaticClass.InnerStaticInStatic.innerStaticStringMethod("foo", 2)""",
      JavaLibClass.InnerStaticClass.InnerStaticInStatic.innerStaticStringMethod("foo", 2),
      Java.boxed.String)
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.innerStaticIntMethod",
      JavaLibClass.InnerStaticClass.InnerStaticInStatic.innerStaticIntMethod,
      Java.boxed.Integer)
  }

  @Ignore("TODO O-7263 Fails as it doesn't see methods of field's value - generating stubs doesn't support local operations on Java fields of type AnyVal")
  @Test
  def nestedClassStaticFieldsIntegration(): Unit = {
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt - 2.0",
      JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt - 2.0, Java.boxed.Double)
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt + 2.0",
      JavaLibClass.InnerStaticClass.InnerStaticInStatic.staticInt + 2.0, Java.boxed.Double)
  }

  @Test
  def nestedClassStaticMethodsIntegration(): Unit = {
    eval("""JavaLibClass.InnerStaticClass.InnerStaticInStatic.innerStaticStringMethod("foo", 2) + false""",
      JavaLibClass.InnerStaticClass.InnerStaticInStatic.innerStaticStringMethod("foo", 2) + false,
      Java.boxed.String)
    eval("JavaLibClass.InnerStaticClass.InnerStaticInStatic.innerStaticIntMethod - 1.2",
      JavaLibClass.InnerStaticClass.InnerStaticInStatic.innerStaticIntMethod - 1.2,
      Java.boxed.Double)
  }

  @Test
  def getValuesOfStaticFieldsOfInterface(): Unit = {
    eval("JavaLibInterface.staticString", JavaLibInterface.staticString, Java.boxed.String)
    eval("JavaLibInterface.staticInt", JavaLibInterface.staticInt, Java.boxed.Integer)
  }

  @Test
  def checkEqualityOfStaticFieldsOfInterface(): Unit = {
    eval("JavaLibInterface.staticString == JavaLibInterface.staticString", true, Java.boxed.Boolean)
    eval("JavaLibInterface.staticString == 114", false, Java.boxed.Boolean)
    eval("JavaLibInterface.staticString != JavaLibInterface.staticString", false, Java.boxed.Boolean)
    eval("JavaLibInterface.staticString != 114", true, Java.boxed.Boolean)

    eval("JavaLibInterface.staticInt == JavaLibInterface.staticInt", true, Java.boxed.Boolean)
    eval("JavaLibInterface.staticInt == 6565", false, Java.boxed.Boolean)
    eval("JavaLibInterface.staticInt != JavaLibInterface.staticInt", false, Java.boxed.Boolean)
    eval("JavaLibInterface.staticInt != 6565", true, Java.boxed.Boolean)
  }
}

object JavaStaticFieldsAndMethodsTest extends BaseIntegrationTestCompanion(JavaTestCase)
