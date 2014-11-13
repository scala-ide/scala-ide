package org.scalaide.core.compiler

import org.scalaide.core.testsetup.TestProjectSetup
import org.junit.Assert._
import org.junit.Test
import NamePrinterTest.scalaCompilationUnit

object NamePrinterTest extends TestProjectSetup("name-printer")

class NamePrinterTest {
  import NamePrinterTest._

  @Test
  def testWithTrivialClass() {
    testWith("TrivialClass.scala", "a.test.pgk.name.TestClass")
  }

  @Test
  def testWithTrivialObject() {
    testWith("TrivialObject.scala", "a.TestObject")
  }

  @Test
  def testWithTypeArg() {
    testWith("TypeArg.scala", "scala.Predef.String")
  }

  @Test
  def testWithMethodArg() {
    testWith("MethodArg.scala", "scala.collection.mutable.Set")
  }

  @Test
  def testWithMethod() {
    testWith("Method.scala", "a.pkg.Klasse.method(i: scala.Int)")
  }

  @Test
  def testWithCurriedMethod() {
    testWith("CurriedMethod.scala", "Good.curry(i: scala.Int)(l1: scala.Long, l2: scala.Long)(s1: java.lang.String, s2: java.lang.String, s3: java.lang.String)")
  }

  @Test
  def testWithTrivialGenericTrait() {
    testWith("TrivialGenericTrait.scala", "TrivialGenericTrait[T]")
  }

  @Test
  def testWithTrivialTrait() {
    testWith("TrivialTrait.scala", "TrivialTrait")
  }

  @Test
  def testWithGenericMethod() {
    testWith("GenericMethod.scala", "GenericMethod.generic[T](obj: T)")
  }

  private def testWith(input: String, expected: Option[String]) {
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

  private def testWith(input: String, expected: String) {
    testWith(input, Option(expected))
  }

}
