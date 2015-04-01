package org.scalaide.debug.internal.expression

import org.junit.runner.RunWith
import org.junit.runners.Suite
import org.scalaide.debug.internal.ui.completion.SimpleContentProposalProviderTest
import org.scalaide.debug.internal.ui.completion.SimpleContentProposalProviderIntegrationTest
import org.scalaide.debug.internal.expression.proxies.primitives.PrimitivesOperationsTestSuite
import org.scalaide.debug.internal.expression.conditional.ConditionalBreakpointsTest
import org.scalaide.debug.internal.expression.features.FeaturesTestSuite
import org.scalaide.debug.internal.expression.proxies.phases.PhasesTestSuite
import org.scalaide.debug.internal.expression.remote.RemoteTestSuite

@RunWith(classOf[Suite])
@Suite.SuiteClasses(Array(
  classOf[ExpressionManagerTest],
  classOf[FeaturesTestSuite],
  classOf[PrimitivesOperationsTestSuite],
  classOf[DifferentStackFramesTest],
  classOf[PhasesTestSuite],
  classOf[ConditionalBreakpointsTest],
  classOf[SimpleContentProposalProviderTest],
  classOf[SimpleContentProposalProviderIntegrationTest],
  classOf[RemoteTestSuite]
))
class ExpressionEvaluatorTestSuite
