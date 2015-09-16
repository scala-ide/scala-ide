/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.ui.completion

import org.eclipse.jface.fieldassist.IContentProposal
import org.junit.Assert._
import org.junit.Ignore
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues.CodeCompletionTestCase

class SimpleContentProposalProviderIntegrationTest extends BaseIntegrationTest(SimpleContentProposalProviderIntegrationTest) {

  @Test
  def testGetProposals(): Unit = {
    // GIVEN proposal provider and debug context
    val provider = new SimpleContentProposalProvider
    val prefix = "mysteriousV"
    runCode("1")

    // WHEN retrieve proposals for given prefix
    val proposals = provider.getProposals(prefix, prefix.length())

    // THEN only expected proposal is returned and its type is correct (variables shadowing works as expected)
    assertEquals(1, proposals.length)
    checkProposal(proposals.head, "alue", "mysteriousValue: scala.Double")
  }

  @Test
  def testGetProposalForFieldOfTypeNull(): Unit = {
    // GIVEN proposal provider and debug context
    val provider = new SimpleContentProposalProvider
    val prefix = "someNull"
    runCode("1")

    // WHEN retrieve proposals for given prefix
    val proposals = provider.getProposals(prefix, prefix.length())

    // THEN required field of Null type is found and case when class couldn't be loaded via JDI is handled
    assertTrue("There should be at least one found proposal", !proposals.isEmpty)
    // count of proposals (1 or 2) depends on version of Scala but in both cases this proposal to check is the first one
    checkProposal(proposals.head, "", prefix + """: Cannot load returned type - debug.CodeCompletion$""")
  }

  private def checkProposal(p: IContentProposal, expectedContent: String, expectedLabel: String): Unit = {
    assertEquals(expectedContent, p.getContent())
    assertEquals(expectedLabel, p.getLabel())
    assertNull(p.getDescription())
  }
}

object SimpleContentProposalProviderIntegrationTest extends BaseIntegrationTestCompanion(CodeCompletionTestCase)