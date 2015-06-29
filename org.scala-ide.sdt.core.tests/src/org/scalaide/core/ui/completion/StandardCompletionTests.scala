package org.scalaide.core.ui.completion

import org.junit.Test
import org.junit.AfterClass
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.ui.internal.preferences.EditorPreferencePage
import org.scalaide.core.FlakyTest

object StandardCompletionTests extends CompletionTests
class StandardCompletionTests {

  import StandardCompletionTests._

  @Test
  def completeMethodWithoutParameterList() = """
    class A {
      def foo = 0
    }
    object X {
      (new A).fo^
    }
  """ becomes """
    class A {
      def foo = 0
    }
    object X {
      (new A).foo^
    }
  """ after Completion("foo: Int")

  @Test
  def completeMethodWithParameterList() = """
    class A {
      def foo(i: Int) = 0
    }
    object X {
      (new A).fo^
    }
  """ becomes """
    class A {
      def foo(i: Int) = 0
    }
    object X {
      (new A).foo([[i]])^
    }
  """ after Completion("foo(i: Int): Int")

  @Test
  def completeMethodWithMultipleParameterLists() = """
    class A {
      def foo(i: Int)(j: Int) = 0
    }
    object X {
      (new A).fo^
    }
  """ becomes """
    class A {
      def foo(i: Int)(j: Int) = 0
    }
    object X {
      (new A).foo([[i]])([[j]])^
    }
  """ after Completion("foo(i: Int)(j: Int): Int")

  @Test
  def doNotInsertImplicitParameterList() = """
    class A {
      def foobar(i: Int, j: Int)(ident: Int)(implicit l: Int) = 0
    }
    object X {
      (new A).foob^
    }
  """ becomes """
    class A {
      def foobar(i: Int, j: Int)(ident: Int)(implicit l: Int) = 0
    }
    object X {
      (new A).foobar([[i]], [[j]])([[ident]])^
    }
  """ after Completion("foobar(i: Int, j: Int)(ident: Int)(l: Int): Int", expectedNumberOfCompletions = 1)

  @Test
  def completeImportedMembers() = """
    class A {
      def foo(i: Int) = 0
    }
    object X {
      val a = new A
      import a._
      fo^
    }
  """ becomes """
    class A {
      def foo(i: Int) = 0
    }
    object X {
      val a = new A
      import a._
      foo([[i]])^
    }
  """ after Completion("foo(i: Int): Int")

  @Test
  def completeMethodWithEmptyParamList() = """
    class Ticket1000475 {
      val v = new Object
      v.toS^
    }
  """ becomes """
    class Ticket1000475 {
      val v = new Object
      v.toString()^
    }
  """ after Completion("toString(): String")

  @Test
  def completeWhenNothingIsAlreadyTyped() = """
    class Ticket1000475 {
      val v = new Object
      v.^
    }
  """ becomes """
    class Ticket1000475 {
      val v = new Object
      v.toString()^
    }
  """ after Completion("toString(): String")

  @Test
  def completeInPostfixNotation() = """
    class Ticket1000475 {
      val v = new Object
      v toS^
    }
  """ becomes """
    class Ticket1000475 {
      val v = new Object
      v toString()^
    }
  """ after Completion("toString(): String")

  @Test
  def completeInInfixNotation() = """
    class Ticket1000475 {
      val m = Map(1 -> "1")
      m(1) foral^
      println()
    }
  """ becomes """
    class Ticket1000475 {
      val m = Map(1 -> "1")
      m(1) forall { [[x]] => [[???]] }^
      println()
    }
  """ after Completion("forall(p: Char => Boolean): Boolean")

  @Test
  def completeJavaType() = """
    class Ticket1000476 {
      val a = new ArrayLis^
    }
  """ becomes """
    import java.util.ArrayList

    class Ticket1000476 {
      val a = new ArrayList^
    }
  """ after Completion("ArrayList - java.util",
      expectedCompletions = Seq(
          "ArrayList - java.util.Arrays",
          "ArrayList - java.util",
          "ArrayLister"))

  @Test
  def completeJavaTypeWithFullyQualifiedIdent() = """
    class Ticket1000476 {
      val a = new java.util.ArrayLis^
    }
  """ becomes """
    class Ticket1000476 {
      val a = new java.util.ArrayList^
    }
  """ after Completion("ArrayList")

  @Test
  def completeJavaTypeSplitUpOverMultipleLines() = """
    class Ticket1000476 {
      val a =
        new
          java
            .util.TreeS^
    }
  """ becomes """
    class Ticket1000476 {
      val a =
        new
          java
            .util.TreeSet^
    }
  """ after Completion("TreeSet")

  @Test
  def completeOverloadedMethod() = """
    class C {
      def t1000654_a(i: Int) = 0
      def t1000654_a(s: String) = 0
    }
    class Test1 {
      new C().t1000654^
    }
  """ becomes """
    class C {
      def t1000654_a(i: Int) = 0
      def t1000654_a(s: String) = 0
    }
    class Test1 {
      new C().t1000654_a([[s]])^
    }
  """ after Completion("t1000654_a(s: String): Int",
      expectedCompletions = Seq(
          "t1000654_a(i: Int): Int",
          "t1000654_a(s: String): Int"))

  @Test
  def completeCaseClassMember() = """
    object T1001014 {
      case class A(val xx: Int)
      val a = A(42)
      a.x^
    }
  """ becomes """
    object T1001014 {
      case class A(val xx: Int)
      val a = A(42)
      a.xx^
    }
  """ after Completion("xx")

  @Test
  def completePackageNameInImport() = """
    import java.u^
    class T1207
  """ becomes """
    import java.util^
    class T1207
  """ after Completion("util - java", expectedNumberOfCompletions = 1)

  @Test
  def completeOnlyMethodNameInImport() = """
    object Ticket1001125 {
      def doNothingWith(that: Any): Unit = {}
    }
    class Ticket1001125 {
      import Ticket1001125.doNo^
    }
  """ becomes """
    object Ticket1001125 {
      def doNothingWith(that: Any): Unit = {}
    }
    class Ticket1001125 {
      import Ticket1001125.doNothingWith^
    }
  """ after Completion(
      "doNothingWith(that: Any): Unit",
      expectedNumberOfCompletions = 1)

  @Test
  def noEmptyParensForJavaGetter() = FlakyTest.retry("noEmptyParensForJavaGetter") { """
    object Test {
      val a = "test string"
      a.getB^
    }
  """ becomes """
    object Test {
      val a = "test string"
      a.getBytes^
    }
  """ after Completion(
      "getBytes(): Array[Byte]",
      expectedNumberOfCompletions = 4)
  }

  @Test
  def emptyParensForJavaNonGetter() = """
    object Test {
      val a: java.io.File = new java.io.File("")
      a.canR^
    }
  """ becomes """
    object Test {
      val a: java.io.File = new java.io.File("")
      a.canRead()^
    }
  """ after Completion(
      "canRead(): Boolean",
      expectedNumberOfCompletions = 1)

  @Test
  def completeParameterInHigherOrderFunction() = """
    object Test {
      def withResource[T](f: T => Unit): Unit = ???
      this.withR^
    }
  """ becomes """
    object Test {
      def withResource[T](f: T => Unit): Unit = ???
      this.withResource { [[x]] => [[???]] }^
    }
  """ after Completion(
      "withResource[T](f: T => Unit): Unit",
      expectedNumberOfCompletions = 1)

  @Test
  def completeParameterInHOFWithEmptyParens() = """
    object Test {
      def lzyEval(f: () => Any): Unit = ???
      this.lzy^
    }
  """ becomes """
    object Test {
      def lzyEval(f: () => Any): Unit = ???
      this.lzyEval { () => [[???]] }^
    }
  """ after Completion(
      "lzyEval(f: () => Any): Unit",
      expectedNumberOfCompletions = 1)

  @Test
  def completeCallByNameParam() = """
    object Test {
      def cbn(f: => Any): Unit = ???
      this.cb^
    }
  """ becomes """
    object Test {
      def cbn(f: => Any): Unit = ???
      this.cbn([[f]])^
    }
  """ after Completion(
      "cbn(f: => Any): Unit",
      expectedNumberOfCompletions = 1)

  @Test
  def completeParameterInHOFWithoutInfixNotation() = {
    import SDTTestUtils._

    withWorkspacePreference(EditorPreferencePage.P_ENABLE_HOF_COMPLETION, false) {
      """
      object Test {
        def withResource[T](f: T => Unit): Unit = ???
        this.withR^
      }
    """ becomes """
      object Test {
        def withResource[T](f: T => Unit): Unit = ???
        this.withResource([[f]])^
      }
    """ after Completion(
          "withResource[T](f: T => Unit): Unit",
          expectedNumberOfCompletions = 1)
    }
  }

  @Test
  def completeParameterInHOFWithInfixNotation() = {
    import SDTTestUtils._

    withWorkspacePreference(EditorPreferencePage.P_ENABLE_HOF_COMPLETION, false) {
      """
      object Test {
        def withResource[T](f: T => Unit): Unit = ???
        this withR^
      }
    """ becomes """
      object Test {
        def withResource[T](f: T => Unit): Unit = ???
        this withResource { [[x]] => [[???]] }^
      }
    """ after Completion(
          "withResource[T](f: T => Unit): Unit",
          expectedNumberOfCompletions = 1)
    }
  }
}