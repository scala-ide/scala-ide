package org.scalaide.core.ui

import org.scalaide.core.testsetup.TestProjectSetup
import org.scalaide.core.CompilerTestUtils
import org.junit.Test
import org.junit.Assert
import org.scalaide.ui.internal.editor.outline._
import org.hamcrest.core.IsInstanceOf

object OutlineModelTest extends TestProjectSetup("outline-model") {
  val unit = scalaCompilationUnit("/pack/Target.scala")

  val compUtils = new CompilerTestUtils(unit)
}

class OutlineModelTest {
  def childAt(p: Node, pos: Int*): Node = {
    if (pos.length == 0)
      p
    else
      p match {
        case cn: ContainerNode => childAt(cn.children.values.toIndexedSeq(pos.head), pos.tail: _*)
        case _ => throw new IllegalArgumentException
      }
  }

  @Test
  def testImport(): Unit = {
    runTest("""package pack
               import scala.Any
            """, rn => {
      Assert.assertEquals(2, rn.children.size)
      Assert.assertEquals("import declarations", childAt(rn, 1).displayName)
      Assert.assertEquals("scala.Any", childAt(rn, 1, 0).displayName)
    })
  }

  @Test
  def testType(): Unit = {
    runTest("""package pack
               class Foo{
                type MyType =Int
               }
            """, rn => {
      Assert.assertEquals(2, rn.children.size)
      Assert.assertEquals("Foo", childAt(rn, 1).displayName)
      Assert.assertEquals("MyType", childAt(rn, 1, 1).displayName)
      Assert.assertTrue(childAt(rn, 1, 1).isInstanceOf[TypeNode])
    })
  }

  @Test
  def testFunArg(): Unit = {
    runTest("""package pack
               class Foo{
                def p2= {""}
                def p:Int => Int ={i => i*i}
                def a(f:Int =>MyType)(implicit a:Int,  b:Long) = {}
                def a1(f:(Int, Long) =>String) = {}
                def a2(f:((Int, Double)) =>String) = {}
               }
            """, rn => {
      Assert.assertEquals(2, rn.children.size)
      Assert.assertEquals("Foo", childAt(rn, 1).displayName)
      Assert.assertEquals("p2", childAt(rn, 1, 1).displayName)
      Assert.assertEquals("p: Int => Int", childAt(rn, 1, 2).displayName)
      Assert.assertEquals("a(f: Int => MyType)(implicit a: Int, b: Long)", childAt(rn, 1, 3).displayName)
      Assert.assertEquals("a1(f: (Int, Long) => String)", childAt(rn, 1, 4).displayName)
      Assert.assertEquals("a2(f: ((Int, Double)) => String)", childAt(rn, 1, 5).displayName)
    })
  }

  @Test
  def testInnerFunc():Unit={
    runTest("""package pack
               class Foo{
                 def a(i: Int): Unit ={
                   def b:Int = 0
                 }
               }
            """, rn =>{
      Assert.assertEquals(2, rn.children.size)
      Assert.assertEquals("Foo", childAt(rn, 1).displayName)
      Assert.assertEquals("a(i: Int): Unit", childAt(rn, 1, 1).displayName)
      Assert.assertEquals("b: Int", childAt(rn, 1, 1, 0).displayName)
            })
  }
  private def runTest(str: String, f: RootNode => Unit): Unit = {
    import OutlineModelTest._
    unit.scalaProject.presentationCompiler(comp => {
      val rn = ModelBuilder.buildTree(comp, unit.sourceMap(str.toCharArray).sourceFile)
      f(rn)
    })
  }
}