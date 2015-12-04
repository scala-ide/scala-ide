package debug

object SayHelloWorld {

  def main(args: Array[String]): Unit = {
    while(true) {
      val name = readLine()
      println(s"Hello, $name")
    }
  }

}