package debug

object MainObject extends App {
  object nestedObject {
    case class NestedClass(intFromCtor: Int) {
      def classMethod = {
        val classLocalInt = 7
        100 + intFromCtor // change this with manual drop to frame
      }
    }
  }

  def recursiveMethod(remainingRecursiveCallsCounter: Int): Int = {
    val instance = nestedObject.NestedClass(5) // change this to have frames dropped automatically
    val recursiveMethodLocalInt = instance.classMethod
    if (remainingRecursiveCallsCounter > 0)
      recursiveMethod(remainingRecursiveCallsCounter - 1) + 1 // avoid tail recursion as we need additional frames
    else
      // breakpoint here
      recursiveMethodLocalInt
  }

  def mainMethod() : Unit = {
    val unused = "unused string"
    recursiveMethod(2) // change this to have frames dropped automatically
  }

  mainMethod()

  object CustomThread extends Thread {
    override def run(): Unit = {
      val customThreadLocalString = "some string"
      println("put breakpoint here - we won't execute this println")
    }
  }

  CustomThread.start()
}
