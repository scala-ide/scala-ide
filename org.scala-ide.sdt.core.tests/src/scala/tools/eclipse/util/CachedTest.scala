package scala.tools.eclipse.util
import org.junit.Test
import junit.framework.Assert

class CachedTest {

  val reentrant: Cached[Int] = new Cached[Int] {
    def create() = {
      reentrant(x => x + 1)
    }

    def destroy(x: Int) {
    }
  }

  @Test(expected = classOf[RuntimeException]) def testDeadlock {
    reentrant { x =>
      x + 1
    }
  }

  @Test def testCorrectInit {
    val testVal = new Cached[Int] {
      def create() = {
        42
      }

      def destroy(x: Int) {

      }
    }

    Assert.assertEquals("Initialized value", 42, testVal(x => x))
  }
}