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
  def simpleTypes(): Unit = {
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
  def existentialTests(): Unit = {
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
  def typeTests(): Unit = {
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
  def innerTypeTests(): Unit = {
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
  def functionTests(): Unit = {
    runTest(
      """val target4: (Int, Int) => Option[String]
         val target5: (java.io.File) => String => String => java.io.File
         val target5: (java.io.File => String) => String => java.io.File""",
      List("val target4: (Int, Int) => Option[String]",
        "val target5: File => (String => (String => File))",
        "val target5: (File => String) => (String => File)"))

  }

  @Test
  def modifierTests(): Unit = {
    runTest(
      """import java.io._
         private val target1: java.io.File
         protected[pack] val target2: (Int, String)
         private[pack] val target3: java.io.File
         private[this] object targetObj
         protected def targetM[A, B <: Option[File]](x: B)(y: File*): File
        """,
      List("private val target1: File",
        "protected[pack] val target2: (Int, String)",
        "private[pack] val target3: File",
        "private[this] object targetObj",
        "protected def targetM[A, B <: Option[File]](x: B)(y: File*): File"))
  }

  @Test
  def refinedTypeTests(): Unit = {
    runTest(
      """type targetRefined = String {
            def refinedMethod[T](x: T*): java.io.File
          }
          """,
      List("type targetRefined = String { .. }"))
  }

  @Test
  @Ignore("Current implementation does not print annotations")
  def annotationsTest(): Unit = {
    runTest("""
      @deprecated val target3: (File, List[java.io.File])""",
      List("@deprecated val target3: (File, List[File])"))
  }

  private def runTest(in: String, expected: List[String]): Unit = {
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
    Assert.assertTrue(exp.isEmpty)
  }

  @Test
  def testVar(): Unit = {
    runTestWithMarker(
      """var a:Int=4
         def f():Unit = {
            println(a/**/)
         }
         """.stripMargin,
      "var a: Int")
  }
  @Test
  def testVarAssign(): Unit = {
    runTestWithMarker(
      """var a:Int=4
         def f():Unit = {
           println(a)
           a/**/=5
         }
         """.stripMargin,
      "var a: Int")
  }

  @Test
  def testVarDeclr(): Unit = {
    runTestWithMarker(
      """var a/**/:Int=4
         def f():Unit = {
           println(a)
         }
         """.stripMargin,
      "var a: Int")
  }

  @Test
  def testModifierVarDeclr(): Unit = {
    runTestWithMarker(
      """protected[this] var a/**/:Int=4
         def f():Unit = {
           println(a)
         }
         """.stripMargin,
      "protected[this] var a: Int")
  }
  private def runTestWithMarker(in: String, expected: String): Unit = {
    import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
    import org.eclipse.core.resources.IFile
    import org.scalaide.core.testsetup.SDTTestUtils._
    import org.scalaide.util.eclipse.RegionUtils
    import RegionUtils.RichRegion

    val fullUnit = s"""|package pack
                       |
                       |trait Foo {
                       |  $in
                       |}
                       |""".stripMargin

    val offset = fullUnit.indexOf("/**/") - 1
    changeContentOfFile(unit.getResource().asInstanceOf[IFile], fullUnit)
    val scalaRegion = new org.eclipse.jface.text.Region(unit.sourceMap(fullUnit.toCharArray()).scalaPos(offset), 1)
    unit.withSourceFile((file, comp) => {
      comp.askReload(unit, unit.sourceMap(fullUnit.toCharArray()).sourceFile)
      val r = scalaRegion.toRangePos(file)
      val tree = comp.askTypeAt(r).getOption()
      val tt = tree.get
      val tpe = tt match {
        case comp.ValDef(_, _, tpt, _) => tpt.tpe
        case _ => tt.tpe
      }

      val result = comp.asyncExec({
        comp.declPrinter.defString(tt.symbol)(tpe)
      }).getOrElse("")()
      Assert.assertEquals(expected, result)
    })
  }
}
