package org.scalaide.extensions.saveactions

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
  classOf[RemoveTrailingWhitespaceTest],
  classOf[AddNewLineAtEndOfFileTest],
  classOf[AddMissingOverrideTest],
  classOf[RemoveDuplicatedEmptyLinesTest],
  classOf[AddReturnTypeToPublicSymbolsTest],
  classOf[TabToSpaceConverterTest]
))
class SaveActionTestSuite
