package org.scalaide.core.ui.completion

import org.junit.runner.RunWith
import org.junit.runners.Suite

@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
    classOf[CompletionOverwriteTests],
    classOf[AccessibilityTests],
    classOf[StandardCompletionTests],
    classOf[ParameterCompletionTests]
))
class CompletionTestSuite