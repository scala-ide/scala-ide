package debug

object Values extends TestApp {

  def foo() {
    val byte: Byte = 4
    val byte2: Byte = 3
    val short: Short = 6
    val short2: Short = 5
    val int = 1
    val int2 = 2
    val double = 1.1
    val double2 = 2.3
    val float = 1.1f
    val float2 = 0.7f
    val char = 'c'
    val char2 = 'd'
    val boolean = false
    val boolean2 = true
    val string = "Ala"
    val list = List(1, 2, 3)
    val multilist = List(List(1), List(2, 3))
    val * = 1
    val long = 1l
    val long2 = 2l
    val libClass = LibClass(1)
    val anyVal: LibAnyVal = 2

    val objectVal = Libs
    val objectList = List(Libs, Libs)

    val nullVal = null
    val nullValString: String = null
    val nullValArray: Array[Int] = null

    val listArray = multilist.toArray
    val intArray = list.toArray

    def testFunction() {
      val debug = "ala"
    }

    def multipleParamers(a: Int)(b: Int) = a + b

    testFunction()

    // number of bottom line must be specified in org.scalaide.debug.internal.expression.integration.TestValues object because a lot of tests use this line
    val debug = ???
  }

  val outer = "ala"

  foo() //this line number appears in AppObject test

  override def toString() = "object Ala"

  def alaMethod(count: Int) = "ala " + count
}
