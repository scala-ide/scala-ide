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

  def nameAt(p: Node, pos: Int*)={
    new ScalaOutlineLabelProvider().getText(childAt(p, pos: _*))
  }

  @Test
  def testImport(): Unit = {
    runTest("""package pack
               import scala.Any
            """, rn => {
      Assert.assertEquals(2, rn.children.size)
      Assert.assertEquals("import declarations", nameAt(rn, 1))
      Assert.assertEquals("scala.Any", nameAt(rn, 1, 0))
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
      Assert.assertEquals("Foo", nameAt(rn, 1))
      Assert.assertEquals("MyType", nameAt(rn, 1, 1))
      Assert.assertTrue(childAt(rn, 1, 1).isInstanceOf[TypeNode])
    })
  }

  @Test
  def testFuncArg(): Unit = {
    runTest("""package pack
               class Foo{
                def p2= {""}
                def p:Int => Int ={i => i*i}
                def a(f:Int =>MyType)(implicit a:Int,  b:Long) = {}
                def a1(f:(Int, Long) =>String) = {}
                def a2(f:((Int, Double)) =>String) = {}
                def a3(i:String):(Long,Int) ={(1,1)}
                def a4(i: => (Int,Long)) ={}
               }
            """, rn => {
      Assert.assertEquals(2, rn.children.size)
      Assert.assertEquals("Foo", nameAt(rn, 1))
      Assert.assertEquals("p2", nameAt(rn, 1, 1))
      Assert.assertEquals("p: Int => Int", nameAt(rn, 1, 2))
      Assert.assertEquals("a(f: Int => MyType)(implicit a: Int, b: Long)", nameAt(rn, 1, 3))
      Assert.assertEquals("a1(f: (Int, Long) => String)", nameAt(rn, 1, 4))
      Assert.assertEquals("a2(f: ((Int, Double)) => String)", nameAt(rn, 1, 5))
      Assert.assertEquals("a3(i: String): (Long, Int)", nameAt(rn, 1, 6))
      Assert.assertEquals("a4(i: => (Int, Long))", nameAt(rn, 1, 7))
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
      Assert.assertEquals("Foo", nameAt(rn, 1))
      Assert.assertEquals("a(i: Int): Unit", nameAt(rn, 1, 1))
      Assert.assertEquals("b: Int", nameAt(rn, 1, 1, 0))
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