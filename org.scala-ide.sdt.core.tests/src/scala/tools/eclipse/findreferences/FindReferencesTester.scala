package scala.tools.eclipse.findreferences

import scala.tools.eclipse.javaelements.SymbolNameUtil

trait FindReferencesTester {

  object Element {
    implicit def element2testBuilder(e: Element): TestDef = new TestDef(e)
  }

  abstract class Element {
    /** The fully qualified name for the element.*/
    def fullName: String
  }

  case class Method(fullName: String) extends Element
  object Method {
    def apply(fullName: String, args: List[String]): Method = Method(fullName + args.mkString("(", "'", ")"))
  }
  case class FieldVar(fullName: String) extends Element
  case class FieldVal(fullName: String) extends Element
  case class Clazz(fullName: String) extends Element
  case class TypeAlias(fullName: String) extends Element
  case class Module(fullName: String) extends Element

  def method(fullName: String, args: List[String] = Nil): Element = Method(fullName, args)
  def fieldVar(fullName: String): Element = FieldVar(fullName)
  def fieldVal(fullName: String): Element = FieldVal(fullName)
  def clazz(fullName: String): Element = Clazz(fullName)
  def clazzConstructor(classFullName: String, args: List[String] = Nil): Element = Method(constructorFullName(classFullName), args)
  def module(fullName: String): Element = Module(fullName + SymbolNameUtil.MODULE_SUFFIX_STRING)
  def moduleConstructor(fullName: String, args: List[String] = Nil): Element = Method(constructorFullName(fullName + SymbolNameUtil.MODULE_SUFFIX_STRING), args)
  def typeAlias(fullName: String): Element = TypeAlias(fullName)

  private def constructorFullName(classFullName: String): String = {
    val constructorMethodName = classFullName.split('.').last
    classFullName + "." + constructorMethodName
  }

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