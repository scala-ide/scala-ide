package scala.tools.eclipse.ui

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
  classOf[ScalaAutoIndentStrategyTest],
  classOf[BracketAutoEditStrategyTest],
  classOf[CommentAutoEditStrategyTest],
  classOf[LiteralAutoEditStrategyTest],
  classOf[StringAutoEditStrategyTest],
  classOf[MultiLineStringAutoEditStrategyTest]))
class UITestSuite