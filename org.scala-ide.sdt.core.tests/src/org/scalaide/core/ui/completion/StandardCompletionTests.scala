package org.scalaide.core.ui.completion

import org.junit.Test

class StandardCompletionTests extends CompletionTests {

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
  """ after Completion("foo(Int): Int")

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
  """ after Completion("foo(Int)(Int): Int")

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
      "doNothingWith(Any): Unit",
      expectedNumberOfCompletions = 1)
}