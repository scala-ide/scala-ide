package traits

import scala.annotation._
import scala.reflect.BeanProperty
import org.junit.Test

abstract class C[T](_x: Int, _y: T) extends Ordered[String] {

  // constructor
  def this(y: T) {
    this(0, y)
  }

  // fields
  val x      = 10
  lazy val lz = x + 1
  var v       = "some string"
  @volatile var volVar = new Object
  final val CONSTANT   = "some string"

  @BeanProperty val beanVal: Int = 10
  @BeanProperty var beanVar: Int = 10

  // methods
  def nullaryMethod =
    (x > 0 && (_y != x))

  def method() {
    System.out.println("Hello, world " + x)
    println(x)
  }

  @Test
  def annotatedMethod {

  }

  def curriedMethod(x: Int)(y: Int) = x + y

  // modifiers
  private def nullaryMethod1 =
    (x > 0 && _y != x)

  private[this] def method1() {
    System.out.println("Hello, world " + x)
    println(x)
  }

  private[traits] def method2() {
    System.out.println("Hello, world " + x)
    println(x)
  }

  protected def curriedMethod1(x: Int)(y: Int) = x + y

  case class InnerC(x: Int = 42)

  // types
  type T = List[Int]
  type U[X] >: Null <: Ordered[X]

  // poly types
  def map[U >: T](f: T => U) = null
  def takeArray(xs: Array[List[String]]) = null
  def takeArray2(xss: Array[Array[Byte]]) = null


  // local definitions
  def localClass(x: Int) = {
    val t = "abc"
    new Runnable {
      override def run() {
        println(t + x)
      }
    }
  }

  def localMethod(x: Int): Int = {
    def local(y: Long) =
      x + 1

    local(10)
  }

  def localVals(x: Int): Long = {
    val y = 10L
    lazy val lz = y + 1
    type LocalT = Int

    def foo(x: LocalT) = x

    foo(x)
  }
}
