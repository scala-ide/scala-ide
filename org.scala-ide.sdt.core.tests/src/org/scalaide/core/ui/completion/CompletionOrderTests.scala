package org.scalaide.core.ui.completion

import org.junit.Test

object CompletionOrderTests extends CompletionTests

class CompletionOrderTests {

  import CompletionOrderTests._

  @Test
  def completeFieldOnTop() = """
    package completeFieldOnTop
    object X extends App {
      val ClassName = ""
      def f(name: String) = name
      f(Class^)
    }
  """ becomes """
    package completeFieldOnTop
    object X extends App {
      val ClassName = ""
      def f(name: String) = name
      f(ClassName^)
    }
  """ after Completion("ClassName",
      expectedCompletions = Seq("ClassName", "ClassManifest", "Class"),
      respectOrderOfExpectedCompletions = true)


  @Test
  def completeMethodOnTop() = """
    package completeMethodOnTop
    object X extends App {
      def ClassName = ""
      def f(name: String) = name
      f(Class^)
    }
  """ becomes """
    package completeMethodOnTop
    object X extends App {
      def ClassName = ""
      def f(name: String) = name
      f(ClassName^)
    }
  """ after Completion("ClassName: String",
      expectedCompletions = Seq("ClassName: String", "ClassManifest", "Class"),
      respectOrderOfExpectedCompletions = true)
}