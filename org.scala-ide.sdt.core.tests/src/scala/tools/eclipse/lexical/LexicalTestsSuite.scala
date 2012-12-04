package scala.tools.eclipse.lexical

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
  classOf[ScalaPartitionTokeniserTest],
  classOf[StringTokenScannerTest]))
class LexicalTestsSuite
