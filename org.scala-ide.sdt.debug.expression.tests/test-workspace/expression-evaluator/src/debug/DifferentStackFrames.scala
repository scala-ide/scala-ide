package debug

object DifferentStackFrames extends TestApp {

  val init = 1

  def recFunction(input: Int): Int = {
    if (input == 0)
      2
    else
      recFunction(input - 1) + 5
  }

  def compute() {
    val input = 5

    recFunction(input - 2)
  }

  Thread.currentThread().setName("main-thread")
  DeamonThread.start()
  Thread.sleep(10) //make sure that demon is sleeping
  compute()
}


object DeamonThread extends Thread {

  setName("lib-deamon")

  def compute1(input: Int) = compute2(222)

  def compute2(input: Int) = {
    input + 4

    //sleep for the end of test
    Thread.sleep(1000000)
  }


  override def run(): Unit = {
    compute1(111)
  }
}
