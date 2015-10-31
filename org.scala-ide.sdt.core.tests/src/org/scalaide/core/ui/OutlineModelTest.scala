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

  def textAt(p: Node, pos: Int*) = {
    new ScalaOutlineLabelProvider().getText(childAt(p, pos: _*))
  }

  @Test
  def testImport(): Unit = {
    runTest("""package pack
               import scala.Any
            """, rn => {
      Assert.assertEquals(2, rn.children.size)
      Assert.assertEquals("import declarations", textAt(rn, 1))
      Assert.assertEquals("scala.Any", textAt(rn, 1, 0))
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
      Assert.assertEquals("Foo", textAt(rn, 1))
      Assert.assertEquals("MyType", textAt(rn, 1, 0))
      Assert.assertTrue(childAt(rn, 1, 0).isInstanceOf[TypeNode])
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
                def a5(i:(Int,Long)*) ={}
               }
            """, rn => {
      Assert.assertEquals(2, rn.children.size)
      Assert.assertEquals("Foo", textAt(rn, 1))
      Assert.assertEquals("p2", textAt(rn, 1, 0))
      Assert.assertEquals("p: Int => Int", textAt(rn, 1, 1))
      Assert.assertEquals("a(f: Int => MyType)(implicit a: Int, b: Long)", textAt(rn, 1, 2))
      Assert.assertEquals("a1(f: (Int, Long) => String)", textAt(rn, 1, 3))
      Assert.assertEquals("a2(f: ((Int, Double)) => String)", textAt(rn, 1, 4))
      Assert.assertEquals("a3(i: String): (Long, Int)", textAt(rn, 1, 5))
      Assert.assertEquals("a4(i: => (Int, Long))", textAt(rn, 1, 6))
      Assert.assertEquals("a5(i: (Int, Long)*)", textAt(rn, 1, 7))
    })
  }

  @Test
  def testInnerFunc(): Unit = {
    runTest("""package pack
               class Foo{
                 def a(i: Int): Unit ={
                   def b:Int = 0
                 }
               }
            """, rn => {
      Assert.assertEquals(2, rn.children.size)
      Assert.assertEquals("Foo", textAt(rn, 1))
      Assert.assertEquals("a(i: Int): Unit", textAt(rn, 1, 0))
      Assert.assertEquals("b: Int", textAt(rn, 1, 0, 0))
    })
  }

  @Test
  def testClassOnly(): Unit = {
    runTest("""package pack
               class Foo{
                 def p ={""}
               }
            """, rn => {
      Assert.assertEquals(2, rn.children.size)
      Assert.assertEquals("Foo", textAt(rn, 1))
      Assert.assertEquals(1, childAt(rn, 1).asInstanceOf[ContainerNode].children.size)
      Assert.assertEquals("p", textAt(rn, 1, 0))
    })
  }

  @Test
  def testTupleVal(): Unit = {
    runTest("""package pack
               class Foo{
                 val (t1, t2) =(1, 2)
               }
            """, rn => {
      Assert.assertEquals(2, rn.children.size)
      Assert.assertEquals("Foo", textAt(rn, 1))
      Assert.assertEquals(2, childAt(rn, 1).asInstanceOf[ContainerNode].children.size)
      Assert.assertEquals("t1", textAt(rn, 1, 0))
      Assert.assertEquals("t2", textAt(rn, 1, 1))
    })
  }

  @Test
  def testTruncate(): Unit = {
    runTest("""package pack
               class Foo{
                 val b:x.y.Z
                 def a(x:x.y.Z):a.b.C=c
               }
            """, rn => {
      Assert.assertEquals(2, rn.children.size)
      Assert.assertEquals("Foo", textAt(rn, 1))
      Assert.assertEquals(2, childAt(rn, 1).asInstanceOf[ContainerNode].children.size)
      Assert.assertEquals("b: Z", textAt(rn, 1, 0))
      Assert.assertEquals("a(x: Z): C", textAt(rn, 1, 1))
    })
  }

  @Test
  def testTypeParameter(): Unit = {
    runTest("""package pack
               class Foo{
                 def a[A](x:A)= 0
                 val x:List[X]
               }
            """, rn => {
      Assert.assertEquals(2, rn.children.size)
      Assert.assertEquals("Foo", textAt(rn, 1))
      Assert.assertEquals(2, childAt(rn, 1).asInstanceOf[ContainerNode].children.size)
      Assert.assertEquals("a[A](x: A)", textAt(rn, 1, 0))
      Assert.assertEquals("x: List[X]", textAt(rn, 1, 1))
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