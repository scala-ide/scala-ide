package org.scalaide.core
package quickassist

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.junit.AfterClass
import org.junit.Assert
import org.junit.BeforeClass
import org.junit.Test
import testsetup.SDTTestUtils
import scala.util.control.Exception
import org.scalaide.core.internal.quickassist.abstractimpl.ImplAbstractMembers
import org.scalaide.core.internal.quickassist.abstractimpl.AbstractMemberProposal

object ImplAbstractMemberTest extends QuickAssistTest {
  @BeforeClass
  def createProject() = create("assist")

  @AfterClass
  def deleteProject() = delete()
}

/** This test suite requires the UI. */
class ImplAbstractMemberTest extends QuickAssistTestHelper {
  import ImplAbstractMemberTest._

  val quickAssist = new ImplAbstractMembers

  def createSource(packageName: String, unitName: String)(contents: String) = createSourceFile(packageName, unitName)(contents)

  def assistsFor(contents: String, expected: String): Unit =
    runQuickAssistWith(contents) { p =>
      Assert.assertTrue("Abstract member not found", p.nonEmpty)

      val displayString = p.head.getDisplayString()
      Assert.assertEquals("Changes unexpected", expected, displayString)
    }

  def assistsNumFor(contents: String, expected: Int) = {
    val unit = createSource("test", "Test.scala")(contents.filterNot(_ == '^'))

    try {
      val Seq(pos) = SDTTestUtils.positionsOf(contents.toCharArray(), "^")
      val proposals = quickAssist.compute(InvocationContext(unit, pos, 0, Nil))
      Assert.assertTrue("Abstract member not found", proposals.nonEmpty)

      val abstractNum = proposals.filter(_.isInstanceOf[AbstractMemberProposal]).size
      Assert.assertEquals("Incorrect num", expected, abstractNum)
    } finally
      unit.delete(true, null)
  }

  @Test
  def assistAbstrDef() {
    assistsFor("""
        trait TestTrait {
          def foo: Int
        }
        class Test extends TestTrait {
          ^
        }
        """.stripMargin, "Implement def 'foo(): Int'")
  }

  @Test
  def assistAbstrVal() {
    assistsFor("""
        trait TestTrait {
          val foo: Int
        }
        class Test extends TestTrait {
          ^
        }
        """.stripMargin, "Implement val 'foo(): Int'")
  }

  @Test
  def assistAbstrVar() {
    assistsFor("""
        trait TestTrait {
          var foo: Int
        }
        class Test extends TestTrait {
          ^
        }
        """.stripMargin, "Implement var 'foo(): Int'")
  }

  @Test
  def assistAbstrDefWithParams1() {
    assistsFor("""
        trait TestTrait {
          def foo(x: Double): Int
        }
        class Test extends TestTrait {
          ^
        }
        """.stripMargin, "Implement def 'foo(Double): Int'")
  }

  @Test
  def assistAbstrDefWithParams2() {
    assistsFor("""
        trait TestTrait {
          def foo(x: Double, y: Map[Int, Float]): Int
        }
        class Test extends TestTrait {
          ^
        }
        """.stripMargin, "Implement def 'foo(Double, Map[Int,Float]): Int'")
  }

  @Test
  def assistAbstrDefWithParams3() {
    assistsFor("""
        trait TestTrait {
          def foo(x: Double, y: Int)(z: Int): Int
        }
        class Test extends TestTrait {
          ^
        }
        """.stripMargin, "Implement def 'foo(Double, Int)(Int): Int'")
  }

  @Test
  def assistAbstrDefWithParams4() {
    assistsFor("""
        trait TestTrait {
          def foo(x: Double, y: Int)(w: Int)(z: String): Int
        }
        class Test extends TestTrait {
          ^
        }
        """.stripMargin, "Implement def 'foo(Double, Int)(Int)(String): Int'")
  }

  @Test
  def assistAbstrDefWithParams5() {
    assistsFor("""
        trait TestTrait {
          def foo(x: Double, y: Int)()(z: String): Int
        }
        class Test extends TestTrait {
          ^
        }
        """.stripMargin, "Implement def 'foo(Double, Int)()(String): Int'")
  }

  @Test
  def assistAbstrDefWithTypeParams1() {
    assistsFor("""
        trait TestTrait {
          def foo[T](y: Int): Int
        }
        class Test extends TestTrait {
          ^
        }
        """.stripMargin, "Implement def 'foo[T](Int): Int'")
  }

  @Test
  def assistAbstrDefWithTypeParams2() {
    assistsFor("""
        trait TestTrait {
          def foo[X, Y, Z](y: Int): Int
        }
        class Test extends TestTrait {
          ^
        }
        """.stripMargin, "Implement def 'foo[X,Y,Z](Int): Int'")
  }

  @Test
  def assistNoAbstrDef() {
    noAssistsFor("""
        class Test extends TestTrait {
          ^
        }
        """.stripMargin)
  }

  def testEnv(keyw: String, method: String = "") = s"""
    abstract class AbstrClass {
      val foo1: Int
      var foo2: Double
      def foo3: Double
    }
    trait TestTrait1 {
      def test1[X](y: Int): Int
      def test2(x: Int): Double
      def test3: Float
    }
    trait TestTrait2 extends TestTrait1 {
      def test3 = 1.0F
      val bar1: List[Int]
      var bar2: Int
      def bar3: Double
    }
    $keyw Test extends AbstrClass with TestTrait2 {
      $method
      ^
    }
  """.stripMargin

  @Test
  def assistAbstrDefNumInClass() {
    assistsNumFor(testEnv("class"), 8)
  }

  @Test
  def assistAbstrDefNumInAbstrClass() {
    assistsNumFor(testEnv("abstract class"), 8)
  }

  @Test
  def assistAbstrDefNumInTrait() {
    assistsNumFor(testEnv("trait"), 8)
  }

  @Test
  def assistAbstrDefNumInClassWithMethod() {
    assistsNumFor(testEnv("class", "def foo3 = 42.0"), 7)
  }

  @Test
  def assistAbstrDefNumInAbstrClassWithMethod() {
    assistsNumFor(testEnv("abstract class", "def foo3 = 42.0"), 7)
  }

  @Test
  def assistAbstrDefNumInTraitWithMethod() {
    assistsNumFor(testEnv("trait", "def foo3 = 42.0"), 7)
  }
}
