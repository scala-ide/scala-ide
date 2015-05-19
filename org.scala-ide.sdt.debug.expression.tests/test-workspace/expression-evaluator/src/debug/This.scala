package debug

object This extends TestApp {

  trait ThisTrait {
    val traitParam = 1

    def traitMethod() = 1
  }

  class ThisClass extends ThisTrait {
    val classParam = 2

    def classMethod() = 2
  }

  object ThisObject extends ThisClass {
    val objectParam = 3

    def objectMethod() = 3

    def testMethod(): Unit = {
      def innerTestMethodWithLambda(): Unit = {
        Some(1).map { e =>
          val debug = 1
        }
      }

      innerTestMethodWithLambda()
    }
  }

  ThisObject.testMethod()
}