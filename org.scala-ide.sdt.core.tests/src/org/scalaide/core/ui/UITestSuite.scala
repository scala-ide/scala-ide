package org.scalaide.core.ui

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
//  classOf[TestScalaIndenter],
  classOf[CommentAutoEditStrategyTest],
  classOf[LiteralAutoEditStrategyTest],
  classOf[StringAutoEditStrategyTest],
  classOf[MultiLineStringAutoEditStrategyTest],
  classOf[IndentGuideGeneratorTest],
  classOf[MultiLineStringAutoIndentStrategyTest],
  classOf[AutoIndentStrategyTest]
))
class UITestSuite
