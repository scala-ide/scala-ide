package org.scalaide.core.compiler

import org.scalaide.core.testsetup.TestProjectSetup
import org.junit.Assert._
import org.junit.Test
import NamePrinterTest.scalaCompilationUnit

object NamePrinterTest extends TestProjectSetup("name-printer")

class NamePrinterTest {
  import NamePrinterTest._

  @Test
  def testQnameWithTrivialClass() {
    testQnameWith("TrivialClass.scala", "a.test.pgk.name.TestClass")
  }

  @Test
  def testQnameWithTrivialObject() {
    testQnameWith("TrivialObject.scala", "a.TestObject")
  }

  @Test
  def testQnameWithTypeArg() {
    testQnameWith("TypeArg.scala", "scala.Predef.String")
  }

  @Test
  def testQnameWithMethodArg() {
    testQnameWith("MethodArg.scala", "scala.collection.mutable.Set")
  }

  @Test
  def testQnameWithMethod() {
    testQnameWith("Method.scala", "a.pkg.Klasse.method(i: Int)")
  }

  @Test
  def testQnameWithCurriedMethod() {
    testQnameWith("CurriedMethod.scala", "Good.curry(i: Int)(l1: Long, l2: Long)(s1: String, s2: String, s3: String)")
  }

  @Test
  def testQnameWithTrivialGenericTrait() {
    testQnameWith("TrivialGenericTrait.scala", "TrivialGenericTrait[T]")
  }

  @Test
  def testQnameWithTrivialTrait() {
    testQnameWith("TrivialTrait.scala", "TrivialTrait")
  }

  @Test
  def testQnameWithGenericMethod() {
    testQnameWith("GenericMethod.scala", "GenericMethod.generic[T](obj: T)")
  }

  @Test
  def testQnameWithTopLevelImport() {
    testQnameWith("TopLevelImport.scala", "scala.collection.mutable")
  }

  @Test
  def testQnameWithNestedImport() {
    testQnameWith("NestedImport.scala", "NestedImport")
  }

  @Test
  def testQnameWithMultiImportOnPackage() {
    testQnameWith("MultiImportOnPackage.scala", "scala.collection.mutable")
  }

  @Test
  def testQnameWithMultiImportOnType() {
    testQnameWith("MultiImportOnType.scala", "scala.collection.mutable")
  }

  @Test
  def testQnameWithRenamingImportOnOrigName() {
    testQnameWith("RenamingImportOnOrigName.scala", "scala.collection.mutable")
  }

  @Test
  def testQnameWithRenamingImportOnNewName() {
    testQnameWith("RenamingImportOnNewName.scala", "scala.collection.mutable")
  }

  @Test
  def testQnameWithCaseClassVal() {
    testQnameWith("CaseClassVal.scala", "CaseClassVal.valium")
  }

  @Test
  def testQnameWithTraitVar() {
    testQnameWith("TraitVar.scala", "TraitVar.varrus")
  }

  @Test
  def testQnameWithClassParameter() {
    testQnameWith("ClassParameter.scala", "ClassParameter.paranormal")
  }

  private def testQnameWith(input: String, expected: Option[String]) {
    val cu = scalaCompilationUnit(input)
    val offset = verifyOffset(cu.getSource.indexOf("/**/") - 1)
    val namePrinter = new NamePrinter(scalaCompilationUnit(input))
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
