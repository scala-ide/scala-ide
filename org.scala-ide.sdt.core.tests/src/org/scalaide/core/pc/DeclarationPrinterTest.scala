package org.scalaide.core.pc

import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.core.CompilerTestUtils
import org.junit.Test
import org.junit.Assert
import org.junit.Ignore
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._

object DeclarationPrinterTest extends TestProjectSetup("decl-printer") {
  val unit = scalaCompilationUnit("/pack/Target.scala")

  val compUtils = new CompilerTestUtils(unit)
}

class DeclarationPrinterTest {
  import DeclarationPrinterTest._

  @Test
  def simpleTypes() {
    runTest(
      """import java.io._
         val target1: java.io.File
         val target2: (Int, String)
         val target3: (File, List[File])
         object targetObj
         def targetM[A, B](x: Int)(y: File*): File
        """,
      List("val target1: File",
        "val target2: (Int, String)",
        "val target3: (File, List[File])",
        "object targetObj",
        "def targetM[A, B](x: Int)(y: File*): File"))
  }

  @Test
  def existentialTests() {
    runTest(
      """import java.io.File
         val target4: Class[_]
         val target5: List[_]
         val target6: List[List[_]]
         val target7: List[List[T]]  forSome { type T <: File }""",
      List("val target4: Class[_]",
        "val target5: List[_]",
        "val target6: List[List[_]]",
        "val target7: List[List[T]] forSome { type T <: java.io.File }"))
  }

  @Test
  def typeTests() {
    runTest(
      """type targetPair = (java.io.File, Int)
         type targetGenericPair[+T] = (T, T)
         type targetBounded >: java.io.File
         type targetBoundedTwo <: List[java.io.File] >: Null
         trait targetTrait[+X, -Y <: String]""",
      List("type targetPair = (File, Int)",
        "type targetGenericPair[+T] = (T, T)",
        "type targetBounded >: File",
        "type targetBoundedTwo <: List[File]",
        "abstract trait targetTrait[+X, -Y <: String] extends AnyRef"))
  }

  @Test
  def innerTypeTests() {
    runTest("""import java.io.File
        class Inner1
        trait targetInner2 extends Inner1
        type Inner2 = targetInner2

        val targetInner: Inner1
        val targetPairInner: (Inner1, Inner2)
        """,
      List("abstract trait targetInner2 extends Inner1",
        "val targetInner: Inner1",
        "val targetPairInner: (Inner1, Inner2)"))
  }

  @Test
  def functionTests() {
    runTest(
      """val target4: (Int, Int) => Option[String]
         val target5: (java.io.File) => String => String => java.io.File
         val target5: (java.io.File => String) => String => java.io.File""",
      List("val target4: (Int, Int) => Option[String]",
        "val target5: File => (String => (String => File))",
        "val target5: (File => String) => (String => File)"))

  }

  @Test
  def modifierTests() {
    runTest(
      """import java.io._
         private val target1: java.io.File
         protected[pack] val target2: (Int, String)
         private[this] object targetObj
         protected def targetM[A, B <: Option[File]](x: B)(y: File*): File
        """,
      List("private val target1: File",
        "protected[package pack] val target2: (Int, String)",
        "private[this] object targetObj",
        "protected def targetM[A, B <: Option[File]](x: B)(y: File*): File"))
  }

  @Test
  def refinedTypeTests() {
    runTest(
      """type targetRefined = String {
            def refinedMethod[T](x: T*): java.io.File
          }
          """,
      List("type targetRefined = String { .. }"))
  }

  @Test
  @Ignore("Current implementation does not print annotations")
  def annotationsTest() {
    runTest("""
      @deprecated val target3: (File, List[java.io.File])""",
      List("@deprecated val target3: (File, List[File])"))
  }

  private def runTest(in: String, expected: List[String]) {
    val fullUnit = s"""|package pack
                       |
                       |trait Foo {
                       |  $in
                       |}
                       |""".stripMargin

    var exp = expected
    compUtils.withTargetTrees(fullUnit) { t =>
      unit.scalaProject.presentationCompiler { compiler =>
        val result = compiler.asyncExec {
          t.symbol.initialize
          compiler.declPrinter.defString(t.symbol.asInstanceOf[compiler.Symbol])()
        }.getOption()

        Assert.assertEquals("Wrong type printer", exp.head, result.get)
        exp = exp.tail
      }
    }
  }
}
