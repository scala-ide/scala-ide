package test

import java.io.FileWriter

object ScalaTest {
  def main(args: Array[String]): Unit = {
    (new ScalaTest).foo()
  }
}

class ScalaTest {
  def foo(): Unit = {
    val writer = new FileWriter("launchDelegate.result")
    writer.write("success")
    writer.close
  }
}