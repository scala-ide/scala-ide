/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package features

import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.Names.Java
import org.scalaide.debug.internal.expression.TestValues.NewInstancesTestCase

class NewKeywordTest extends BaseIntegrationTest(NewKeywordTest) {

  @Test(expected = classOf[UnsupportedFeature])
  def traitRefinement(): Unit =
    eval("new A {}", "A", "A")

  @Test(expected = classOf[UnsupportedFeature])
  def classRefinement(): Unit =
    eval("new B {}", "B", "B")

  @Test
  def noArgConstructor(): Unit = eval("new LibClassWithoutArgs", "LibClassWithoutArgs", "debug.LibClassWithoutArgs")

  @Test
  def simpleConstructor(): Unit = eval("new LibClass(1)", "LibClass(1)", "debug.LibClass")

  @Test
  def multiArgumentListConstructor(): Unit =
    eval("new LibClass2Lists(1)(2)", "LibClass2Lists(1)", "debug.LibClass2Lists")

  @Test
  def createNewInstanceOfPrimitiveType(): Unit =
    eval("new java.lang.Integer(12345)", "12345", Java.boxed.Integer)

  @Test
  def scalaVarArgConstructor(): Unit = {
    eval("new LibClassWithVararg()", "LibClassWithVararg(List())", "debug.LibClassWithVararg")
    eval("new LibClassWithVararg(1)", "LibClassWithVararg(List(1))", "debug.LibClassWithVararg")
    eval("new LibClassWithVararg(1, 2)", "LibClassWithVararg(List(1, 2))", "debug.LibClassWithVararg")
  }

  // TODO - Toolbox cannot typecheck java vararg methods https://issues.scala-lang.org/browse/SI-9212
  // If this test fails it means Toolbox is fixed, yay! (or some other exception is thrown)
  @Test(expected = classOf[ReflectiveCompilationFailure])
  def javaVarArgConstructor(): Unit = {
    eval("new JavaVarArg()", "JavaVarArg()", "debug.JavaVarArg")
    eval("new JavaVarArg(1)", "JavaVarArg([1])", "debug.JavaVarArg")
    eval("new JavaVarArg(1, 2)", "JavaVarArg([1, 2])", "debug.JavaVarArg")
  }

  @Test
  def nestedInstantiatedClassField(): Unit =
    eval("(new LibObject.LibNestedClass).LibMoreNestedObject.id", "4", Java.boxed.Integer)
}

object NewKeywordTest extends BaseIntegrationTestCompanion(NewInstancesTestCase)
