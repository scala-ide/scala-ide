/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.debug.internal.ui.completion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.scalaide.debug.internal.expression.BaseIntegrationTest
import org.scalaide.debug.internal.expression.BaseIntegrationTestCompanion
import org.scalaide.debug.internal.expression.TestValues

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
    val p = proposals(0)
    assertEquals("alue", p.getContent())
    assertEquals("mysteriousValue: scala.Double", p.getLabel())
    assertNull(p.getDescription())
  }
}

object SimpleContentProposalProviderIntegrationTest extends BaseIntegrationTestCompanion(
  fileName = TestValues.codeCompletionFileName,
  lineNumber = TestValues.codeCompletionLineNumber)
