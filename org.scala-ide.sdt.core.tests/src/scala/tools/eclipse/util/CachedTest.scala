package scala.tools.eclipse.util
import org.junit.Test

class CachedTest {

  val reentrant: Cached[Int] = new Cached[Int] {
    def create() = {
      println("in create")
      reentrant(x => x + 1)
    }

    def destroy(x: Int) {
    }
  }

  @Test(expected = classOf[RuntimeException]) def testDeadlock {
    reentrant { x =>
      println("in testDeadlock")
      x + 1
    }
  }
}