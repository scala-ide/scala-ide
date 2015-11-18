/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression
package features

import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.UnsupportedFeature
import org.scalaide.debug.internal.preferences.ExprEvalPreferencePage

import Names.Java
import Names.Scala
import TestValues.ImplicitsTestCase

class ImplicitTest extends BaseIntegrationTest(ImplicitTest) {

  @Test
  def testToMapImplicit(): Unit =
    eval("List((1, 2), (2, 3)).toMap", Map(1 -> 2, 2 -> 3), "scala.collection.immutable.Map$Map2")

  @Test
  def testTopLevelImport(): Unit = eval("implicitField[TopLevelImport]", "TopLevelImport", Java.String)

  @Test
  def testClassField(): Unit = eval("implicitField[ClassField]", "ClassField", Java.String)

  @Test
  def `scala collections implicits: List(1, 2).filter(_ > 1).head`(): Unit =
    eval("List(1, 2).filter(_ >= 2)", "List(2)", Scala.::)

  @Test
  def `scala collections implicits: List(1 -> 2, 2 -> 3).toMap`(): Unit =
    eval("List((1, 2), (2, 3)).toMap", "Map(1 -> 2, 2 -> 3)", "scala.collection.immutable.Map$Map2")

  def testWildcardClassImport(): Unit = eval("implicitField[WildcardClassImport]", "WildcardClassImport", Java.String)

  @Test
  def testImplicitClassImport(): Unit = eval("implicitField[ImplicitClassImport]", "ImplicitClassImport", Java.String)

  @Test
  def testLocalImplicitImport(): Unit = eval("implicitField[LocalImplicitImport]", "LocalImplicitImport", Java.String)

  @Test
  def testLocalWildcardImport(): Unit = eval("implicitField[LocalWildcardImport]", "LocalWildcardImport", Java.String)

  @Test
  def testLocalField(): Unit = eval("implicitField[LocalField]", "LocalField", Java.String)

  @Test
  def testCompanionObjectFunctionConversion(): Unit = eval(
    "implicitConversion[CompanionObjectFunctionConversion](1)",
    "CompanionObjectFunctionConversion",
    Java.String)

  @Test
  def testClassFunctionConversion(): Unit = eval(
    "implicitConversion[ClassFunctionConversion](1)",
    "ClassFunctionConversion",
    Java.String)

  @Test
  def testLocalFunctionConversion(): Unit = eval(
    "implicitConversion[LocalFunctionConversion](1)",
    "LocalFunctionConversion",
    Java.String)

  @Test
  def testCompanionObjectFunctionConversionBound(): Unit = eval(
    "implicitBounds(new CompanionObjectFunctionConversion)",
    "CompanionObjectFunctionConversion",
    Java.String)

  @Test
  def testClassFunctionConversionBound(): Unit = eval(
    "implicitBounds(new ClassFunctionConversion)",
    "ClassFunctionConversion",
    Java.String)

  @Test(expected = classOf[UnsupportedFeature])
  def testLocalFunctionConversionBound(): Unit = runCode(
    "implicitBounds(new LocalFunctionConversion)")

  @Test
  def testContextBounds(): Unit = eval("""contextBounds("ContextBounds")""", "ContextBounds", Java.String)

  @Test
  def testClassWithImplicitArgument(): Unit = eval("""new ClassWithImplicitArgument""", "ClassWithImplicitArgument", "debug.ClassWithImplicitArgument")

  @Test
  def testClassWithMultipleArgumentListAndImplicits(): Unit = eval(
    """new ClassWithMultipleArgumentListAndImplicits(1)(2)""",
    "ClassWithMultipleArgumentListAndImplicits",
    "debug.ClassWithMultipleArgumentListAndImplicits")

  @Test
  def testImplicitClass(): Unit = eval("1 --> 2", (1, 2), "scala.Tuple2")

  @Test
  def testExplicitAndImplicitUsageOfVal() = eval("List(localField, implicitField[LocalField])", "List(LocalField, LocalField)", Scala.::)

  @Test
  def testImplicitsConflict(): Unit = expectReflectiveCompilationError("implicitField[Conflict]")

  //TODO make sure that we use correct implicits O-8575
}

object ImplicitTest extends BaseIntegrationTestCompanion(ImplicitsTestCase) {
  @BeforeClass
  def setupForTest(): Unit = {
    ScalaExpressionEvaluatorPlugin().getPreferenceStore.setValue(ExprEvalPreferencePage.AddImportsFromCurrentFile, true)
  }

  @AfterClass
  def tearDownAfterTest(): Unit = {
    ScalaExpressionEvaluatorPlugin().getPreferenceStore.setValue(ExprEvalPreferencePage.AddImportsFromCurrentFile, false)
  }
}
