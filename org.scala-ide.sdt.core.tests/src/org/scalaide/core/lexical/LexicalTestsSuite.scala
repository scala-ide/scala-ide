package org.scalaide.core.lexical

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
  classOf[ScalaPartitionTokeniserTest],
  classOf[StringTokenScannerTest],
  classOf[ScaladocTokenScannerTest],
  classOf[ScalaCodeScannerTest]))
class LexicalTestsSuite
