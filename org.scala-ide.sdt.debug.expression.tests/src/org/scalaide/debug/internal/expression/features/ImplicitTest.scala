/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package features

import org.junit.Ignore
import org.junit.Test

import Names.Java
import Names.Scala

import TestValues.ImplicitsTestCase

class ImplicitTest extends BaseIntegrationTest(ImplicitTest) {

  @Test
  def testToMapImplicit(): Unit =
    eval("List((1, 2), (2, 3)).toMap", Map(1 -> 2, 2 -> 3), "scala.collection.immutable.Map$Map2")

  @Test
  def testTopLevelImport: Unit = eval("implicitField[TopLevelImport]", "TopLevelImport", Java.boxed.String)

  @Test
  def testClassField: Unit = eval("implicitField[ClassField]", "ClassField", Java.boxed.String)

  @Test
  def `scala collections implicits: List(1, 2).filter(_ > 1).head`(): Unit = disableOnJava8 {
    eval("List(1, 2).filter(_ >= 2)", "List(2)", Scala.::)
  }

  @Test
  def `scala collections implicits: List(1 -> 2, 2 -> 3).toMap`(): Unit = disableOnJava8 {
    eval("List((1, 2), (2, 3)).toMap", "Map(1 -> 2, 2 -> 3)", "scala.collection.immutable.Map$Map2")
  }

  def testWildcardClassImport: Unit = eval("implicitField[WildcardClassImport]", "WildcardClassImport", Java.boxed.String)

  @Test
  def testImplicitClassImport: Unit = eval("implicitField[ImplicitClassImport]", "ImplicitClassImport", Java.boxed.String)

  @Test
  def testLocalImplicitImport: Unit = eval("implicitField[LocalImplicitImport]", "LocalImplicitImport", Java.boxed.String)

  @Test
  def testLocalWildcardImport: Unit = eval("implicitField[LocalWildcardImport]", "LocalWildcardImport", Java.boxed.String)

  @Test
  def testLocalField: Unit = eval("implicitField[LocalField]", "LocalField", Java.boxed.String)

  @Test
  def testCompanionObjectFunctionConversion: Unit = eval(
    "implicitConversion[CompanionObjectFunctionConversion](1)",
    "CompanionObjectFunctionConversion",
    Java.boxed.String)

  @Test
  def testClassFunctionConversion: Unit = eval(
    "implicitConversion[ClassFunctionConversion](1)",
    "ClassFunctionConversion",
    Java.boxed.String)

  @Test
  def testLocalFunctionConversion: Unit = eval(
    "implicitConversion[LocalFunctionConversion](1)",
    "LocalFunctionConversion",
    Java.boxed.String)

  @Test
  def testCompanionObjectFunctionConversionBound: Unit = eval(
    "implicitBounds(new CompanionObjectFunctionConversion)",
    "CompanionObjectFunctionConversion",
    Java.boxed.String)

  @Test
  def testClassFunctionConversionBound: Unit = eval(
    "implicitBounds(new ClassFunctionConversion)",
    "ClassFunctionConversion",
    Java.boxed.String)

  @Test(expected = classOf[UnsupportedFeature])
  def testLocalFunctionConversionBound: Unit = runCode(
    "implicitBounds(new LocalFunctionConversion)")

  @Test
  def testContextBounds: Unit = eval("""contextBounds("ContextBounds")""", "ContextBounds", Java.boxed.String)

  @Test
  def testClassWithImplicitArgument: Unit = eval("""new ClassWithImplicitArgument""", "ClassWithImplicitArgument", "debug.ClassWithImplicitArgument")

  @Test
  def testClassWithMultipleArgumentListAndImplicits: Unit = eval(
    """new ClassWithMultipleArgumentListAndImplicits(1)(2)""",
    "ClassWithMultipleArgumentListAndImplicits",
    "debug.ClassWithMultipleArgumentListAndImplicits")

  @Test
  def testImplicitClass: Unit = eval("1 --> 2", (1, 2), "scala.Tuple2")

  @Test
  def testExplicitAndImplicitUsageOfVal = eval("List(localField, implicitField[LocalField])", "List(LocalField, LocalField)", Scala.::)

  @Test
  def testImplicitsConflict: Unit = expectReflectiveCompilationError("implicitField[Conflict]")

  //TODO make sure that we use correct implicits O-8575
}

object ImplicitTest extends BaseIntegrationTestCompanion(ImplicitsTestCase)
