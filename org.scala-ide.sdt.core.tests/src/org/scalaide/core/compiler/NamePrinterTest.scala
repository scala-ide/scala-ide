package org.scalaide.core.compiler

import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import org.scalaide.CompilerSupportTests
import NamePrinterTest.mkScalaCompilationUnit
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.core.FlakyTest

object NamePrinterTest extends CompilerSupportTests

class NamePrinterTest {
  import NamePrinterTest._

  @Test
  def testWithTrivialClass() {
    testWith(
      """|package a.test.pgk.name
         |class TestClass/**/""",
      "a.test.pgk.name.TestClass")
  }

  @Test
  def testWithTrivialObject() {
    testWith(
      """|package a
         |object TestObject/**/""",
      "a.TestObject")
  }

  @Test
  def testWithTypeArg() {
    testWith(
      "class TypeArg(list: List[String/**/])",
      "scala.Predef.String")
  }

  @Test
  def testWithMethodArg() {
    testWith(
      """|import scala.collection.mutable
         |
         |class SomeClass {
         |def someMethod(set: mutable.Set/**/[Int]) = None
         |}""",
      "scala.collection.mutable.Set")
  }

  @Test
  def testWithMethod() {
    testWith(
      """|package a.pkg
         |
         |class Klasse {
         |  def method/**/(i: Int) = 0
         |}""",
      "a.pkg.Klasse.method(i: Int)")
  }

  @Test
  def testWithCurriedMethod() {
    testWith(
      """|class Good {
         |def curry/**/(i: Int)(l1: Long, l2: Long)(s1: String, s2: String, s3: String) = Unit()
         |}""",
      "Good.curry(i: Int)(l1: Long, l2: Long)(s1: String, s2: String, s3: String)")
  }

  @Test
  def testWithTrivialGenericTrait() {
    testWith(
      "trait TrivialGenericTrait/**/[T]",
      "TrivialGenericTrait[T]")
  }

  @Test
  def testWithTrivialTrait() {
    testWith(
      "trait TrivialTrait/**/",
      "TrivialTrait")
  }

  @Test
  def testWithGenericMethod() {
    testWith(
      """|trait GenericMethod {
         |def generic/**/[T <: AnyRef](obj: T): Unit
         |}""",
      "GenericMethod.generic[T](obj: T)")
  }

  @Test
  def testWithTopLevelImport() {
    testWith(
      """|import scala.collection.mutable/**/
         |class TopLevelImport""",
      "scala.collection.mutable")
  }

  @Test
  def testWithNestedImport() {
    testWith(
      """|object NestedImport
         |class NestedImport {
         |  import NestedImport._/**/
         |}""",
      "NestedImport")
  }

  @Test
  def testWithMultiImportOnPackage() {
    testWith(
      """|import scala.collection.mutable/**/.{Set, Map, ListBuffer}
         |class MultiImportOnPackage""",
      "scala.collection.mutable")
  }

  @Test
  def testWithMultiImportOnType() {
    testWith(
      """|import scala.collection.mutable.{Set, Map/**/, ListBuffer}
         |class MultiImportOnType""",
      "scala.collection.mutable")
  }

  @Test
  def testWithRenamingImportOnOrigName() {
    testWith(
      """|import scala.collection.mutable.{Set/**/ => MySet}
         |class RenamingImportOnOrigName""",
      "scala.collection.mutable")
  }

  @Test
  def testWithRenamingImportOnNewName() {
    testWith(
      """|import scala.collection.mutable.{Set => MySet/**/}
         |class RenamingImportOnNewName""",
      "scala.collection.mutable")
  }

  @Test
  def testWithCaseClassVal() {
    testWith(
      "case class CaseClassVal(valium/**/: AnyRef)",
      "CaseClassVal.valium")
  }

  @Test
  def testWithTraitVar() {
    testWith(
      """|trait TraitVar {
         |  var varrus/**/: Int
         |}""",
      "TraitVar.varrus")
  }

  @Test
  def testWithClassParameter() {
    testWith(
      "class ClassParameter(paranormal/**/: String)",
      "ClassParameter.paranormal")
  }

  @Test
  def testWithMethodParamOnDefinition() {
    testWith(
      """|class MethodParamOnDefinition {
         |  def method(param/**/: Int) = param
         |}""",
      "MethodParamOnDefinition.method(param: Int).param")
  }

  @Test
  def testWithMethodParamOnUse() {
    testWith(
      """|class MethodParamOnUse {
         |  def method(param: Int) = param/**/
         |}""",
      "MethodParamOnUse.method(param: Int).param")
  }

  @Test
  def testWithFunctionArgs() {
    testWith(
      """|class FunctionArgs {
         |  def method/**/(f: (Int, String) => Long): Long = f(42, "42")
         |}""",
       "FunctionArgs.method(f: (Int, String) => Long)")
  }

  @Test
  def testWithByNameArg() {
    testWith(
      """|class ByNameArg {
         |  def method/**/(f: => Long): Long = f
         |}""",
       "ByNameArg.method(f: => Long)")
  }

  @Test
  def testWithParentClass() {
    testWith(
      """|package a.b.c
         |class Parent
         |class Child extends Parent/**/""",
       "a.b.c.Parent")
  }

  @Test
  def testWithNestedMethod() {
    testWith(
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
    testWith(
      """|class LocalClass {
         |  def fun(p1: Int) = {
         |    class Local/**/
         |    new Local.hashCode * p1
         |  }
         |}""",
      "LocalClass.fun(p1: Int).Local")
  }

  @Test
  def testWithAnonClassOnDef() = FlakyTest.retry("testWithAnonClassOnDef") {
    testWith(
      """|class AnonClassOnDef {
         |  def fun() {
         |    new java.lang.Runnable() {
         |      def run/**/() = Unit
         |    }.run()
         |  }
         |}""",
      "AnonClassOnDef.fun().new Runnable {...}.run()")
  }

  @Test
  def testWithAnonClassWithMultipleParents() {
    testWith(
      """|trait Trait1
         |trait Trait2
         |class AnonClassWithMultipleParents {
         |  def fun() {
         |    new java.lang.Runnable() with Trait1 with Trait2 {
         |      def run/**/() = Unit
         |    }.run()
         |  }
         |}""",
      "AnonClassWithMultipleParents.fun().new Runnable with Trait1 with Trait2 {...}.run()")
  }

  @Test
  def testWithAnonClassOnCall() {
    testWith(
      """|class AnonClassOnCall {
         |  def fun() {
         |    new java.lang.Runnable() {
         |      def run() = Unit
         |    }.run/**/()
         |  }
         |}""",
      "new Runnable {...}.run")
  }

  @Test
  def testWithDeeplyNestedName() {
    testWith(
      """|package deeply.nested
         |class Ca {
         |  object Ob {
         |    val X = new AnyRef {
         |      def x(a: Int): Int = {
         |        class C {
         |           def x = {
         |              val x = 42
         |              a*x/**/
         |           }
         |         }
         |         (new C).x
         |       }
         |    }.x(3)
         |  }
         |}""",
      "deeply.nested.Ca.Ob.new AnyRef {...}.x(a: Int).C.x.x")
  }

  @Test
  def testWithLocalObject() {
    testWith(
      """|class WithLocalObject {
         |  def fun(x: Int) = {
         |    object Local/**/
         |    Local.hashCode * x
         |  }
         |}""",
      "WithLocalObject.fun(x: Int).Local")
  }

  @Test
  def testWithPackageObject() {
    testWith(
      """|package test.pkg
         |package object obj {
            val Valdemar/**/ = "Gunthar"
         |}""",
      "test.pkg.obj.Valdemar")
  }

  /*
   * Adding support for backticks might be more work than one might initially think.
   */
  @Test
  @Ignore
  def testWithBackticks() {
    testWith(
      """|package `package`
         |class ` `
         |object `object` {
         |  class `class` {
         |     def `with`(`val`: ` `) = {
         |       val `var` = "var"
         |       `var`/**/ hashCode
              }
         |  }
         |}""",
      "`package`.`object`.`class`.`with`(`val`: ` `).`var`")
  }

  @Test
  def testWithAnotherDeeplyNestedName() {
    testWith(
      """|class Using(something: String)
         |class AnotherDeeplyNestedName {
         |  object A {
         |   def wwith(b: String) {
         |     class And {
         |        class K {
         |          object C {
         |            new Using("") {
         |              def c() {
         |                new Using("") {
         |                  def e(f: Int) {
         |                    object G {
         |                      val H/**/ = 3
         |                    }
         |                  }
         |                }
         |              }
         |            }
         |          }
         |        }
         |      }
         |    }
         |  }
         |}""",
      "AnotherDeeplyNestedName.A.wwith(b: String).And.K.C.new Using {...}.c().new Using {...}.e(f: Int).G.H")
  }

  @Test
  def testWithEncodedIdents() {
    testWith(
      """|class :: {
         |  def +(i/**/: Int) = i + 44
         |}""",
      "::.+(i: Int).i")
  }

  @Test
  def testWithLazyVal() {
    testWith(
      """|class WithLazyVal {
         |  lazy val x/**/ = 333
         |}""",
      "WithLazyVal.x")
  }

  @Test
  def testWithMethodCallInNestedStructure() {
    testWith(
      """|object MethodcallInNestedStructure {
         |  def method = 999
         |}
         |class MethodcallInNestedStructure {
         |  def otherMethod {
         |     MethodcallInNestedStructure.method/**/
         |  }
         |}""",
      "MethodcallInNestedStructure.method")
  }

  @Test
  def testWithNamedParamCtorCallInMethod() {
    testWith(
      """|package test.named.param.ctor.call
         |class NamedParamCtorCallInMethod(arg: String) {
         |  def method {
         |    new NamedParamCtorCallInMethod(arg/**/ = "aaahrg")
         |  }
         |}""",
      "test.named.param.ctor.call.NamedParamCtorCallInMethod(arg: String)")
  }

  @Test
  def testWithSimpleAnnotation() {
    testWith(
      """|import scala.annotation._
         |class SimpleAnnotation extends StaticAnnotation
         |@SimpleAnnotation/**/
         |class WithSimpleAnnotation
      """,
      "SimpleAnnotation")
  }

  @Test
  def testWithAnnotationOnDefOnParam() {
    testWith(
      """|import scala.annotation._
         |class AnnotationOnDefOnParam(val name: String) extends StaticAnnotation
         |object AnnotationOnDefOnParam {
         |  @AnnotationOnDefOnParam(name/**/ = "Hallo")
         |  def test = 32
         |}""",
      "AnnotationOnDefOnParam(name: String)")
  }

  @Test
  def testWithAnnotationOnDef() {
    testWith(
      """|import scala.annotation._
         |class AnnotationOnDef(val name: String) extends StaticAnnotation
         |object AnnotationOnDef {
         |  @AnnotationOnDef/**/(name = "Hallo")
         |  def test = 32
         |}""",
      "AnnotationOnDef")
  }

  @Test
  def testWithJavaxGeneratedAnnotationOnClass() {
    testWith(
      """|import javax.annotation._
         |@Generated/**/(Array("today"))
         |class WithJavaxGeneratedAnnotationOnClass""",
      "javax.annotation.Generated")
  }

  @Test
  def testWithJavaxGeneratedAnnotationOnMethod() {
    testWith(
      """|import javax.annotation._
         |class WithJavaxGeneratedAnnotationOnMethod {
         |  @Generated/**/(Array("yesterday"))
         |  def method = Unit
         |}""",
      "javax.annotation.Generated")
  }

  @Test
  def testWithJavaxResourceAnnotationOnMethod() {
    testWith(
      """|import javax.annotation._
         |class WithJavaxResourceAnnotationOnResource {
         |  @Resource/**/(name = "test", lookup = "anywhere")
         |  def method = Unit
         |}""",
      "javax.annotation.Resource")
  }

  @Test
  def testWithAuxillaryCtor() {
    testWith(
      """|class WithAxillaryCtor(str: String) {
         |  def this(i: Int) = this(i.toString)
         |  def foo() { new WithAxillaryCtor(/**/33) }
         |}""",
      "WithAxillaryCtor(i: Int)")
  }

  @Test
  def testWithMethodCallOnGeneric() {
    testWith(
      """|class WithMethodCallOnGeneric[A](a: A) {
         |  def method[B, C](b: B, c: C) = b.hashCode + c.hashCode
         |  def foo = {
         |    val tst = new WithMethodCallOnGeneric("string")
         |    tst.method(/**/1L, 0.0)
         |  }
         |}""",
      "WithMethodCallOnGeneric.method[B, C](b: B, c: C)")
  }

  private def testQnameWith(input: String, expected: Option[String]) {
    val source = input.stripMargin
    val cu = prepareCompilationUnit(source)
    val offset = verifyOffset(source.indexOf("/**/") - 1)
    val namePrinter = new NamePrinter(cu)
    val res = namePrinter.qualifiedNameAt(offset)
    assertEquals(expected, res)
  }

  private def prepareCompilationUnit(source: String) = {
    waitForReconcile(mkScalaCompilationUnit(source, true))
  }

  private def waitForReconcile(cu: ScalaCompilationUnit) = {
    cu.initialReconcile().get match {
      case Left(_) => ()
      case Right(th) => throw th
    }
    cu
  }

  private def verifyOffset(offset: Int) = {
    assert(offset > -1, s"Illegal offset: $offset")
    offset
  }

  private def testWith(input: String, expected: String) {
    testQnameWith(input, Option(expected))
  }

}
