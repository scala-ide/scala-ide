package org.scalaide.core.ui.completion

import org.junit.Test
import org.junit.Ignore

object ParameterCompletionTests extends CompletionTests
class ParameterCompletionTests {

  import ParameterCompletionTests._

  // TODO remove core.completion.CompletionTests.{t1001218,t1001272} once ticket #1002095 is fixed
  @Test @Ignore("not implemented. See #1002095")
  def addParameterNamesWhenCompletionAlreadyExists() = """
    class X {
      def foo() = 0
      def foo(i: Int) = 0
      foo(^)
    }
  """ becomes """
    class X {
      def foo() = 0
      def foo(i: Int) = 0
      foo([[i]]^)
    }
  """ after Completion(
      "foo(Int): Int",
      expectedCompletions = Seq(
          "foo(): Int",
          "foo(Int): Int"))

  @Test @Ignore("not implemented. See #1002095")
  def addParameterNamesToCtor() = """
    class A(a: Int) {
      def this() = this(123)
    }
    object Test {
      val a = new A(^)
    }
  """ becomes """
    class A(a: Int) {
      def this() = this(123)
    }
    object Test {
      val a = new A([[a]]^)
    }
  """ after Completion(
      "A(Int): A",
      expectedCompletions = Seq(
          "A(): A",
          "A(Int): A"))

  @Test @Ignore("not implemented. See #1002095")
  def doNotShowSuperclassCtors() = """
    class A(a: Int) {
      def this() = this(123)
    }
    class B extends A(1)
    object Test {
      val b = new B(^)
    }
  """ becomes """
    class A(a: Int) {
      def this() = this(123)
    }
    class B extends A(1)
    object Test {
      val b = new B(^)
    }
  """ after Completion(
      "B(): B",
      expectedNumberOfCompletions = 1)

  @Test @Ignore("not implemented. See #1002095")
  def showParamsOfInnerClass() = """
    object D {
      class E(i: Int)
    }
    object Test {
      val e = new D.E(^)
    }
  """ becomes """
    object D {
      class E(i: Int)
    }
    object Test {
      val e = new D.E([[i]]^)
    }
  """ after Completion(
      "E(Int): D.E",
      expectedNumberOfCompletions = 1)

  @Test @Ignore("not implemented. See #1002095")
  def showParamsOfInstacenClassCtor() = """
    class A(a: Int) {
      class InnerA(i: Int)
    }
    object Test {
      val a = new A(1)
      new a.InnerA(^)
    }
  """ becomes """
    class A(a: Int) {
      class InnerA(i: Int)
    }
    object Test {
      val a = new A(1)
      new a.InnerA([[i]]^)
    }
  """ after Completion(
      "InnerA(Int): a.InnerA",
      expectedNumberOfCompletions = 1)
}