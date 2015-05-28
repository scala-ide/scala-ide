package test

import java.io.FileWriter
import org.junit.Test

class ScalaTest {
  @Test
  def foo(): Unit = {
    val writer = new FileWriter("launchDelegate.result")
    writer.write("success")
    writer.close
  }
}