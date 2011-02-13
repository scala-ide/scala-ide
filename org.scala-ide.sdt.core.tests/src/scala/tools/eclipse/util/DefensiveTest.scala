package scala.tools.eclipse
package util

import org.junit.{ Test, Assert}

class DefensiveTest {
  // see http://www.assembla.com/spaces/scala-ide/tickets/1000236
  @Test
  def testNoRaiseExceptionForMultiIntArgs() {
    val start = 10
    val end = 3
    if (Defensive.check(start <= end, "setSourceRange0 start %d <= end %d false then force end = start (FIXME)", start, end)) {
      Assert.fail("this code should not be raise")
    }    
  }
}