package annots
import org.junit._

class ScalaTestSuite {

  @Before def prepare(): Unit = {
  }

  @org.junit.Test
  def someTestMethod(): Unit = {
  }

  @Test def anotherTestMethod(): Unit = {

  }
}