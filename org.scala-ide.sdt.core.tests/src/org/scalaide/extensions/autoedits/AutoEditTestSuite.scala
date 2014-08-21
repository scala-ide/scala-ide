package org.scalaide.extensions.autoedits

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
  classOf[ConvertToUnicodeTest],
  classOf[SmartSemicolonInsertionTest],
  classOf[CloseCurlyBraceTest]
))
class AutoEditTestSuite
