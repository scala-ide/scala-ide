package t1000919

import org.junit.Test
import java.io.FileWriter

class ScalaTest {

  @Test
  def f() {
    val writer = new FileWriter("t1000919.result")
    writer.write("t1000919 success")
    writer.close
  }

}