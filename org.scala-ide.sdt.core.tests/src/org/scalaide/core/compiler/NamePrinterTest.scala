package org.scalaide.core.compiler

import scala.annotation.elidable
import scala.annotation.elidable.ASSERTION

import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.scalaide.CompilerSupportTests

import NamePrinterTest.mkScalaCompilationUnit

object NamePrinterTest extends CompilerSupportTests

class NamePrinterTest {
  import NamePrinterTest._

  @Test
  def testQnameWithTrivialClass() {
    testQnameWith(
      """|package a.test.pgk.name
         |class TestClass/**/""",
      "a.test.pgk.name.TestClass")
  }

  @Test
  def testQnameWithTrivialObject() {
    testQnameWith(
      """|package a
         |object TestObject/**/""",
      "a.TestObject")
  }

  @Test
  def testQnameWithTypeArg() {
    testQnameWith(
      "class TypeArg(list: List[String/**/])",
      "scala.Predef.String")
  }

  @Test
  def testQnameWithMethodArg() {
    testQnameWith(
      """|import scala.collection.mutable
         |
         |class SomeClass {
         |def someMethod(set: mutable.Set/**/[Int]) = None
         |}""",
      "scala.collection.mutable.Set")
  }

  @Test
  def testQnameWithMethod() {
    testQnameWith(
      """|package a.pkg
         |
         |class Klasse {
         |  def method/**/(i: Int) = 0
         |}""",
      "a.pkg.Klasse.method(i: Int)")
  }

  @Test
  def testQnameWithCurriedMethod() {
    testQnameWith(
      """|class Good {
         |def curry/**/(i: Int)(l1: Long, l2: Long)(s1: String, s2: String, s3: String) = Unit()
         |}""",
      "Good.curry(i: Int)(l1: Long, l2: Long)(s1: String, s2: String, s3: String)")
  }

  @Test
  def testQnameWithTrivialGenericTrait() {
    testQnameWith(
      "trait TrivialGenericTrait/**/[T]",
      "TrivialGenericTrait[T]")
  }

  @Test
  def testQnameWithTrivialTrait() {
    testQnameWith(
      "trait TrivialTrait/**/",
      "TrivialTrait")
  }

  @Test
  def testQnameWithGenericMethod() {
    testQnameWith(
      """|trait GenericMethod {
         |def generic/**/[T <: AnyRef](obj: T): Unit
         |}""",
      "GenericMethod.generic[T](obj: T)")
  }

  @Test
  def testQnameWithTopLevelImport() {
    testQnameWith(
      """|import scala.collection.mutable/**/
         |class TopLevelImport""",
      "scala.collection.mutable")
  }

  @Test
  def testQnameWithNestedImport() {
    testQnameWith(
      """|object NestedImport
         |class NestedImport {
         |  import NestedImport._/**/
         |}""",
      "NestedImport")
  }

  @Test
  def testQnameWithMultiImportOnPackage() {
    testQnameWith(
      """|import scala.collection.mutable/**/.{Set, Map, ListBuffer}
         |class MultiImportOnPackage""",
      "scala.collection.mutable")
  }

  @Test
  def testQnameWithMultiImportOnType() {
    testQnameWith(
      """|import scala.collection.mutable.{Set, Map/**/, ListBuffer}
         |class MultiImportOnType""",
      "scala.collection.mutable")
  }

  @Test
  def testQnameWithRenamingImportOnOrigName() {
    testQnameWith(
      """|import scala.collection.mutable.{Set/**/ => MySet}
         |class RenamingImportOnOrigName""",
      "scala.collection.mutable")
  }

  @Test
  def testQnameWithRenamingImportOnNewName() {
    testQnameWith(
      """|import scala.collection.mutable.{Set => MySet/**/}
         |class RenamingImportOnNewName""",
      "scala.collection.mutable")
  }

  @Test
  def testQnameWithCaseClassVal() {
    testQnameWith(
      "case class CaseClassVal(valium/**/: AnyRef)",
      "CaseClassVal.valium")
  }

  @Test
  def testQnameWithTraitVar() {
    testQnameWith(
      """|trait TraitVar {
         |  var varrus/**/: Int
         |}""",
      "TraitVar.varrus")
  }

  @Test
  def testQnameWithClassParameter() {
    testQnameWith(
      "class ClassParameter(paranormal/**/: String)",
      "ClassParameter.paranormal")
  }

  @Test
  def testQnameWithMethodParamOnDefinition() {
    testQnameWith(
      """|class MethodParamOnDefinition {
         |  def method(param/**/: Int) = param
         |}""",
      "MethodParamOnDefinition.method(param: Int).param")
  }

  @Test
  def testQnameWithMethodParamOnUse() {
    testQnameWith(
      """|class MethodParamOnUse {
         |  def method(param: Int) = param/**/
         |}""",
      "MethodParamOnUse.method(param: Int).param")
  }

  @Test
  def testWithFunctionArgs() {
    testQnameWith(
      """|class FunctionArgs {
         |  def method/**/(f: (Int, String) => Long): Long = f(42, "42")
         |}""",
       "FunctionArgs.method(f: (Int, String) => Long)")
  }

  @Test
  def testWithByNameArg() {
    testQnameWith(
      """|class ByNameArg {
         |  def method/**/(f: => Long): Long = f
         |}""",
       "ByNameArg.method(f: => Long)")
  }

  @Test
  def testWithParentClass() {
    testQnameWith(
      """|package a.b.c
         |class Parent
         |class Child extends Parent/**/""",
       "a.b.c.Parent")
  }

  @Test
  def testWithNestedMethod() {
    testQnameWith(
      """|class NestedMethod {
         |  def nest(p1: Int) = {
         |    def fun(p: => Int) = {
         |      val x = p*p1
         |      x/**/
         |    }
         |    fun(33)
         |  }
         |}""",
      "NestedMethod.nest(p1: Int).fun(p: => Int).x")
  }

  @Test
  def testWithLocalClass() {
    testQnameWith(
      """|class LocalClass {
         |  def fun(p1: Int) = {
         |    class Local/**/
         |    new Local.hashCode * p1
         |  }
         |}""",
      "LocalClass.fun(p1: Int).Local")

  }

  private def testQnameWith(input: String, expected: Option[String]) {
    val source = input.stripMargin
    val cu = mkScalaCompilationUnit(source)
    val offset = verifyOffset(source.indexOf("/**/") - 1)
    val namePrinter = new NamePrinter(cu)
    val res = namePrinter.qualifiedNameAt(offset)
    assertEquals(expected, res)
  }

  private def verifyOffset(offset: Int) = {
    assert(offset > -1, s"Illegal offset: $offset")
    offset
  }

  private def testQnameWith(input: String, expected: String) {
    testQnameWith(input, Option(expected))
  }

}
