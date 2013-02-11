package scala.tools.eclipse.ui

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
//  classOf[TestScalaIndenter],
  classOf[BracketAutoEditStrategyTest],
  classOf[CommentAutoEditStrategyTest]))
class UITestSuite