package debug

object SayHelloWorld {

  def main(args: Array[String]) {
    val name = readLine()
    println(s"Hello, $name")
  }

}