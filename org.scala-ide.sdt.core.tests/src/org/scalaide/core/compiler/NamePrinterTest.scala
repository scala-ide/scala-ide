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
    testQnameWith("Method.scala", "a.pkg.Klasse.method(i: scala.Int)")
  }

  @Test
  def testQnameWithCurriedMethod() {
    testQnameWith("CurriedMethod.scala", "Good.curry(i: scala.Int)(l1: scala.Long, l2: scala.Long)(s1: java.lang.String, s2: java.lang.String, s3: java.lang.String)")
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
