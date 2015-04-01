package debug

object Visibility extends TestApp {

  trait VisibilityTrait {
    protected val traitParam = 1

    protected def traitMethod() = 1
  }

  class VisibilityClass extends VisibilityTrait {
    protected val classParam = 2

    protected def classMethod() = 2
  }

  object VisibilityObject extends VisibilityClass {
    private val objectParam = 3

    private def objectMethod() = 3

    def testMethod(): Unit = {
      val debug = 1
    }
  }

  VisibilityObject.testMethod()
}