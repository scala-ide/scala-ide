package org.scalaide.extensions.autoedits

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
  classOf[ConvertToUnicodeTest],
  classOf[SmartSemicolonInsertionTest],
  classOf[CloseCurlyBraceTest],
  classOf[JumpOverClosingCurlyBraceTest],
  classOf[RemoveCurlyBracePairTest],
  classOf[CloseParenthesisTest],
  classOf[CloseBracketTest],
  classOf[CloseAngleBracketTest],
  classOf[RemoveParenthesisPairTest],
  classOf[CreateMultiplePackageDeclarationsTest],
  classOf[ApplyTemplateTest],
  classOf[RemoveBracketPairTest],
  classOf[RemoveAngleBracketPairTest]
))
class AutoEditTestSuite
