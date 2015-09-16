package debug

object SayHelloWorld {

  def main(args: Array[String]): Unit = {
    val name = readLine()
    println(s"Hello, $name")
  }

}