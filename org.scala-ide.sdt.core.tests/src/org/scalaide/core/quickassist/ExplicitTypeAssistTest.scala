package org.scalaide.core
package quickassist

import org.eclipse.jdt.internal.core.util.SimpleDocument
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import org.scalaide.core.internal.quickassist.explicit.ExplicitReturnType
import scala.util.control.Exception

object ExplicitTypeAssistTest extends QuickAssistTest {
  @BeforeClass
  def createProject() = create("assist")

  @AfterClass
  def deleteProject() = delete()
}

/** This test suite requires the UI. */
class ExplicitTypeAssistTest extends QuickAssistTestHelper {
  import ExplicitTypeAssistTest._

  val quickAssist = new ExplicitReturnType

  def createSource(packageName: String, unitName: String)(contents: String) = createSourceFile(packageName, unitName)(contents)

  def assistsFor(contents: String, expected: String): Unit =
    runQuickAssistWith(contents) { p =>
      Assert.assertTrue("Add explicit type proposal not found", p.nonEmpty)

      val doc = new SimpleDocument(contents.filterNot(_ == '^'))
      p.head.apply(doc)
      Assert.assertEquals("Changes unexpected", expected, doc.get())
    }

  @Test
  def assistVal() {
    assistsFor("""
        class Test {
          val foo = ^42
        }
        """.stripMargin, """
        class Test {
          val foo: Int = 42
        }
        """.stripMargin)
  }

  @Test
  def assistDef() {
    assistsFor("""
        class Test {
          def foo(x: Int) = ^x + 1
        }
        """.stripMargin, """
        class Test {
          def foo(x: Int): Int = x + 1
        }
        """.stripMargin)
  }

  @Test
  def assistList() {
    assistsFor("""
        class Test {
          def foo(x: Int) = ^List.fill(x)(0)
        }
        """.stripMargin, """
        class Test {
          def foo(x: Int): List[Int] = List.fill(x)(0)
        }
        """.stripMargin)
  }

  @Test
  def assistMultiLine() {
    assistsFor("""
        class Test {
          def foo(x: Int) = ^{
            List.fill(x)(0)
          }
        }
        """.stripMargin, """
        class Test {
          def foo(x: Int): List[Int] = {
            List.fill(x)(0)
          }
        }
        """.stripMargin)
  }

  @Test
  def assistComplexSignature() {
    assistsFor("""
        class Test {
          def foo[T](size: Int = 42, init: T)(implicit ord: Ordered[T]) = {
            ^List.fill(size)(init)
          }
        }
        """.stripMargin, """
        class Test {
          def foo[T](size: Int = 42, init: T)(implicit ord: Ordered[T]): List[T] = {
            List.fill(size)(init)
          }
        }
        """.stripMargin)
  }

  @Test
  def assistInnerScopeVal() {
    assistsFor("""
        class Test {
          def foo(x: Int) = {
            val size = 10
            val bar = ^List.fill(size)(0)
          }
        }
        """.stripMargin, """
        class Test {
          def foo(x: Int) = {
            val size = 10
            val bar: List[Int] = List.fill(size)(0)
          }
        }
        """.stripMargin)
  }

  @Test
  def assistInnerScopeDef() {
    assistsFor("""
        class Test {
          def foo(x: Int) = {
            val size = 10
            def bar[T](init: T) = ^List.fill(size)(init)
          }
        }
        """.stripMargin, """
        class Test {
          def foo(x: Int) = {
            val size = 10
            def bar[T](init: T): List[T] = List.fill(size)(init)
          }
        }
        """.stripMargin)
  }

  @Test
  def assistTransitive() {
    assistsFor("""
        class Test {
          val x = ^initialize()

          def initialize() = {
            cout += 1
            count
          }
          var count = 0
        }
        """.stripMargin, """
        class Test {
          val x: Int = initialize()

          def initialize() = {
            cout += 1
            count
          }
          var count = 0
        }
        """.stripMargin)
  }

  @Test
  def assistMultiAssign() {
    assistsFor("""
        class Test {
          val x, y, z = ^initialize()

          def initialize() = 0
        }
        """.stripMargin, """
        class Test {
          val x, y, z: Int = initialize()

          def initialize() = 0
        }
        """.stripMargin)
  }

  @Test
  def noAssistPatMat() {
    noAssistsFor("""
        class Test {
          val Some(x) = ^Option(new Object)
        }
        """.stripMargin)
  }

  @Test
  def noAssistTuple() {
    noAssistsFor("""
        class Test {
          val (x, y) = ^(1, 2)
        }
        """.stripMargin)
  }

  @Test
  def assistOperatorVal() {
    assistsFor("""
        class Test {
          val ~ = ^42
        }
        """.stripMargin, """
        class Test {
          val ~ : Int = 42
        }
        """.stripMargin)
  }

  @Test
  def assistOperatorDef() {
    assistsFor("""
        class Test {
          def ++ = ^42
        }
        """.stripMargin, """
        class Test {
          def ++ : Int = 42
        }
        """.stripMargin)
  }
}
