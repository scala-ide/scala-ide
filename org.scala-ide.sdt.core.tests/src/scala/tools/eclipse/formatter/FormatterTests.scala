package scala.tools.eclipse.formatter

import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.Test
import org.junit.Assert._

object FormatterTests extends TestProjectSetup("general")

class FormatterTests {
  import FormatterTests._
  
  @Test
  def NotNullForInvalidCode_1000891() = {
    val formatter= new ScalaFormatterCleanUpProvider
    val cu= compilationUnit("formatter/NotNullForInvalidCode_1000891.scala")
    assertNotNull("The formatter should not return null as changes", formatter.createCleanUp(cu))
  }

}