package org.scalaide.core.ui.completion

import org.junit.Test
import org.junit.Ignore

object CompletionOverwriteTests extends CompletionTests
class CompletionOverwriteTests {

  import CompletionOverwriteTests._

  @Test
  def doNotOverwriteWhenFeatureDisabled() = """
    package doNotOverwriteWhenFeatureDisabled
    class A {
      def foo(i: Int) = 0
      def bar(i: Int) = 0
    }
    object X {
      (new A).b^foo(3)
    }
  """ becomes """
    package doNotOverwriteWhenFeatureDisabled
    class A {
      def foo(i: Int) = 0
      def bar(i: Int) = 0
    }
    object X {
      (new A).bar([[i]])^foo(3)
    }
  """ after Completion("bar(i: Int): Int")

  @Test
  def doNotOverwriteWhenThereIsNothingToBeOverwritten() = """
    package doNotOverwriteWhenThereIsNothingToBeOverwritten
    class A {
      def foo(i: Int) = 0
    }
    object X {
      (new A).fo^
    }
  """ becomes """
    package doNotOverwriteWhenThereIsNothingToBeOverwritten
    class A {
      def foo(i: Int) = 0
    }
    object X {
      (new A).foo([[i]])^
    }
  """ after Completion("foo(i: Int): Int", enableOverwrite = true)

  @Test
  def overwriteBeforeParamList() = """
    package overwriteBeforeParamList
    class A {
      def foo(i: Int) = 0
      def bar(i: Int) = 0
    }
    object X {
      (new A).b^foo(3)
    }
  """ becomes """
    package overwriteBeforeParamList
    class A {
      def foo(i: Int) = 0
      def bar(i: Int) = 0
    }
    object X {
      (new A).bar^(3)
    }
  """ after Completion("bar(i: Int): Int", enableOverwrite = true)

  @Test
  def overwriteInsideOfParamList() = """
    package overwriteInsideOfParamList
    object X {
      def meth = 0
      val value = 0

      println(me^value)
    }
  """ becomes """
    package overwriteInsideOfParamList
    object X {
      def meth = 0
      val value = 0

      println(meth^)
    }
  """ after Completion("meth: Int", enableOverwrite = true)

  @Test
  def overwriteBeforeInfixMethodCall() = """
    package overwriteBeforeInfixMethodCall
    object X {
      val ident1, ident2 = true
      val x = ide^nt1 && ident1
    }
  """ becomes """
    package overwriteBeforeInfixMethodCall
    object X {
      val ident1, ident2 = true
      val x = ident2^ && ident1
    }
  """ after Completion("ident2", enableOverwrite = true)

  @Test
  def overwriteBeforeMethodCallWithPunctuation() = """
    package overwriteBeforeMethodCallWithPunctuation
    object X {
      val ident1, ident2 = true
      val x = ide^nt1.&&(ident1)
    }
  """ becomes """
    package overwriteBeforeMethodCallWithPunctuation
    object X {
      val ident1, ident2 = true
      val x = ident2^.&&(ident1)
    }
  """ after Completion("ident2", enableOverwrite = true)

  @Test @Ignore("unimplemented, see #1002092")
  def overwriteBeforeParamListWhenNoParensExist() = """
    package overwriteBeforeParamListWhenNoParensExist
    class A {
      def meth(arg: Int) = arg
      def func(arg: Int) = arg
    }
    object X {
      val obj = new A
      val arg = 0
      obj fu^meth arg
    }
  """ becomes """
    package overwriteBeforeParamListWhenNoParensExist
    class A {
      def meth(arg: Int) = arg
      def func(arg: Int) = arg
    }
    object X {
      val obj = new A
      val arg = 0
      obj func^ arg
    }
  """ after Completion("func(Int): Int", enableOverwrite = true)

  @Test
  def overwriteBeforeEndOfLine() = """
    package overwriteBeforeEndOfLine
    object X {
      val ident1, ident2 = 0
      val x = iden^t1
    }
  """ becomes """
    package overwriteBeforeEndOfLine
    object X {
      val ident1, ident2 = 0
      val x = ident2^
    }
  """ after Completion("ident2", enableOverwrite = true)

  @Test
  def overwriteBeforeComment() = """
    package overwriteBeforeComment
    object X {
      val ident1, ident2 = 0
      val x = iden^t1 // comment
    }
  """ becomes """
    package overwriteBeforeComment
    object X {
      val ident1, ident2 = 0
      val x = ident2^ // comment
    }
  """ after Completion("ident2", enableOverwrite = true)

  @Test @Ignore("unimplemented, see #1002093")
  def overwriteBeforeUnderscore() = """
    package overwriteBeforeUnderscore
    object X {
      def func(i: Int) = i
      def meth(i: Int) = i
      val x = me^func _
    }
  """ becomes """
    package overwriteBeforeUnderscore
    object X {
      def func(i: Int) = i
      def meth(i: Int) = i
      val x = meth^ _
    }
  """ after Completion("meth(Int): Int", enableOverwrite = true)
}
