package scala.tools.eclipse.findreferences

import scala.tools.eclipse.javaelements.SymbolNameUtil

trait FindReferencesTester {

  object Element {
    implicit def element2testBuilder(e: Element): TestDef = new TestDef(e)
  }

  abstract class Element {
    def name: String
  }

  case class Method(name: String) extends Element
  case class FieldVar(name: String) extends Element
  case class FieldVal(name: String) extends Element
  case class Clazz(name: String) extends Element
  case class TypeAlias(name: String) extends Element
  case class Module(name: String) extends Element

  def method(name: String): Element = Method(name)
  def fieldVar(name: String): Element = FieldVar(name)
  def fieldVal(name: String): Element = FieldVal(name)
  def clazz(name: String): Element = Clazz(name)
  def clazzConstructor(name: String): Element = Method(name)
  def module(name: String): Element = Module(name + SymbolNameUtil.MODULE_SUFFIX_STRING)
  def moduleConstructor(name: String): Element = Method(name + SymbolNameUtil.MODULE_SUFFIX_STRING)
  def typeAlias(name: String): Element = TypeAlias(name)

  class TestDef(e: Element) {
    def isReferencedBy(that: Element): FindReferencesTestBuilder =
      new FindReferencesTestBuilder(e, Set(that))
  }

  abstract class TestBuilder {
    def testMarker: String
    def toExpectedTestResult: TestResult
  }

  class FindReferencesTestBuilder(source: Element, referencedBy: Set[Element]) extends TestBuilder {
    override def testMarker: String = "/*ref*/"

    def and(that: Element): FindReferencesTestBuilder =
      new FindReferencesTestBuilder(source, referencedBy + that)

    override def toExpectedTestResult: TestResult = TestResult(source, referencedBy)
  }

  case class TestResult(source: Element, matches: Set[Element])

}