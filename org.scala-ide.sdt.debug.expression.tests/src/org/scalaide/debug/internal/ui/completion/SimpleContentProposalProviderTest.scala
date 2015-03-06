/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.ui.completion

import org.eclipse.jface.fieldassist.ContentProposal
import org.eclipse.jface.fieldassist.IContentProposal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

import SimpleContentProposalProvider.createContentProposalsForStrings
import SimpleContentProposalProvider.shouldBeProposal

class SimpleContentProposalProviderTest {
  import SimpleContentProposalProvider._

  @Test
  def testCurrentNamePrefix() {
    val provider = new SimpleContentProposalProvider
    val expression = "foo .bar\tbaz\nala.ela.ola"

    def checkPrefixForPosition(pos: Int, expectedPrefix: String) =
      assertEquals(expectedPrefix, provider.currentNamePrefix(expression, pos))

    checkPrefixForPosition(0, "")
    checkPrefixForPosition(2, "fo")
    checkPrefixForPosition(3, "foo")
    checkPrefixForPosition(8, ".bar")
    checkPrefixForPosition(9, "")
    checkPrefixForPosition(12, "baz")
    checkPrefixForPosition(13, "")
    checkPrefixForPosition(22, "ala.ela.o")
  }

  @Test
  def testGetProposalsForEnabledCodeCompletion() {
    val proposals = createProposalsList

    val provider = new SimpleContentProposalProvider {
      override val proposalsForCurrentContext = proposals
    }

    def newProposals(proposal: IContentProposal, charsToSkip: Int) =
      new ContentProposal(proposal.getContent().substring(charsToSkip), proposal.getLabel(), proposal.getDescription())

    compareProposals(provider.getProposals("barw", 0), proposals)
    compareProposals(provider.getProposals("barw", 2), Seq(0, 1, 2, 4).map(p => newProposals(proposals(p), 2)))
    compareProposals(provider.getProposals("barw", 3), Seq(0, 2, 4).map(p => newProposals(proposals(p), 3)))
    assertTrue(provider.getProposals("ala", 3).isEmpty)
  }

  @Test
  def testgGetProposalsForDisabledCodeCompletion() {
    val provider = new SimpleContentProposalProvider {
      override def isCodeCompletionEnabled() = false
      override val proposalsForCurrentContext = createProposalsList
    }

    assertTrue(provider.getProposals("", 0).isEmpty)
  }

  @Test
  def testCreateContentProposalsForString() {
    val seq = Seq(("content1", "label1"), ("content2", "label2"))
    val typeName = "typeName"

    val proposals = createContentProposalsForStrings(seq, typeName)

    assertEquals(seq.length, proposals.length)
    proposals.zip(seq).foreach {
      case (proposal, (content, label)) =>
        assertEquals(content, proposal.getContent())
        assertEquals(s"$label - $typeName", proposal.getLabel())
        assertNull(proposal.getDescription())
    }
  }

  @Test
  def testShouldBeProposal() {
    assertTrue(shouldBeProposal("unary_-"))
    assertFalse(shouldBeProposal("foo$bar"))
    assertFalse(shouldBeProposal("<init>"))
    assertFalse(shouldBeProposal("<clinit>"))
  }

  private def createProposalsList = List(
    new ContentProposal("bar2", "label_bar2", "description_bar2"),
    new ContentProposal("baz", "label_baz", "description_baz"),
    new ContentProposal("bar", "label_bar", null),
    new ContentProposal("foo", "label_foo", "description_foo"),
    new ContentProposal("barbarian", "label_barbarian", "description_barbarian"))

  private def compareProposals(currentProposals: Seq[IContentProposal], expectedProposals: Seq[IContentProposal]) {
    assertEquals(expectedProposals.length, currentProposals.length)
    currentProposals.zip(expectedProposals).foreach {
      case (current, expected) =>
        assertEquals(expected.getContent(), current.getContent())
        assertEquals(expected.getLabel(), current.getLabel())
        assertEquals(expected.getDescription(), current.getDescription())
    }
  }
}