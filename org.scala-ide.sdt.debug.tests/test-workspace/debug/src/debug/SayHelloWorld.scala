package debug

object SayHelloWorld {
  def main(args: Array[String]): Unit = {
      Thread.sleep(3000)
      val name = readLine()
      println(s"Hello, $name")
  }
}