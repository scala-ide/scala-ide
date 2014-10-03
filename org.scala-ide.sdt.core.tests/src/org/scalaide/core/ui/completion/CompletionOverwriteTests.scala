package org.scalaide.core.ui.completion

import org.junit.Test
import org.junit.Ignore
import org.scalaide.core.FlakyTest

object CompletionOverwriteTests extends CompletionTests
class CompletionOverwriteTests {

  import CompletionOverwriteTests._

  @Test
  def doNotOverwriteWhenFeatureDisabled() = FlakyTest.retry("doNotOverwriteWhenFeatureDisabled") {
    """
    class A {
      def foo(i: Int) = 0
      def bar(i: Int) = 0
    }
    object X {
      (new A).b^foo(3)
    }
  """ becomes """
    class A {
      def foo(i: Int) = 0
      def bar(i: Int) = 0
    }
    object X {
      (new A).bar([[i]])^foo(3)
    }
  """ after Completion("bar(i: Int): Int")
  }

  @Test
  def doNotOverwriteWhenThereIsNothingToBeOverwritten() = FlakyTest.retry("doNotOverwriteWhenThereIsNothingToBeOverwritten") {
    """
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
  """ after Completion("foo(i: Int): Int", enableOverwrite = true)
  }

  @Test
  def overwriteBeforeParamList() = FlakyTest.retry("overwriteBeforeParamList") {
    """
    class A {
      def foo(i: Int) = 0
      def bar(i: Int) = 0
    }
    object X {
      (new A).b^foo(3)
    }
  """ becomes """
    class A {
      def foo(i: Int) = 0
      def bar(i: Int) = 0
    }
    object X {
      (new A).bar^(3)
    }
  """ after Completion("bar(i: Int): Int", enableOverwrite = true)
  }

  @Test
  def overwriteInsideOfParamList() = FlakyTest.retry("overwriteInsideOfParamList") {
    """
    object X {
      def meth = 0
      val value = 0

      println(me^value)
    }
  """ becomes """
    object X {
      def meth = 0
      val value = 0

      println(meth^)
    }
  """ after Completion("meth: Int", enableOverwrite = true)
  }

  @Test
  def overwriteBeforeInfixMethodCall() = FlakyTest.retry("overwriteBeforeInfixMethodCall") {
    """
    object X {
      val ident1, ident2 = true
      val x = ide^nt1 && ident1
    }
  """ becomes """
    object X {
      val ident1, ident2 = true
      val x = ident2^ && ident1
    }
  """ after Completion("ident2", enableOverwrite = true)
  }

  @Test
  def overwriteBeforeMethodCallWithPunctuation() = FlakyTest.retry("overwriteBeforeMethodCallWithPunctuation") {
    """
    object X {
      val ident1, ident2 = true
      val x = ide^nt1.&&(ident1)
    }
  """ becomes """
    object X {
      val ident1, ident2 = true
      val x = ident2^.&&(ident1)
    }
  """ after Completion("ident2", enableOverwrite = true)
  }

  @Test @Ignore("unimplemented, see #1002092")
  def overwriteBeforeParamListWhenNoParensExist() = """
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
  def overwriteBeforeEndOfLine() = FlakyTest.retry("overwriteBeforeEndOfLine") {
    """
    object X {
      val ident1, ident2 = 0
      val x = iden^t1
    }
  """ becomes """
    object X {
      val ident1, ident2 = 0
      val x = ident2^
    }
  """ after Completion("ident2", enableOverwrite = true)
  }

  @Test
  def overwriteBeforeComment() = FlakyTest.retry("overwriteBeforeComment") {
    """
    object X {
      val ident1, ident2 = 0
      val x = iden^t1 // comment
    }
  """ becomes """
    object X {
      val ident1, ident2 = 0
      val x = ident2^ // comment
    }
  """ after Completion("ident2", enableOverwrite = true)
  }

  @Test @Ignore("unimplemented, see #1002093")
  def overwriteBeforeUnderscore() = """
    object X {
      def func(i: Int) = i
      def meth(i: Int) = i
      val x = me^func _
    }
  """ becomes """
    object X {
      def func(i: Int) = i
      def meth(i: Int) = i
      val x = meth^ _
    }
  """ after Completion("meth(Int): Int", enableOverwrite = true)
}
