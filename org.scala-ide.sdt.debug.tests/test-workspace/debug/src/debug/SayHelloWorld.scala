package debug

object SayHelloWorld {
  def main(args: Array[String]): Unit = {
      Thread.sleep(5000)
      val name = readLine()
      println(s"Hello, $name")
  }
}