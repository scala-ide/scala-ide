package org.scalaide.core.ui.completion

import org.junit.Test
import org.junit.Assert._
import org.scalaide.core.completion.ProposalRelevanceCalculator

class ProposalRelevanceCalculatorTest {
  private val calc = new ProposalRelevanceCalculator

  private def makeSureThatJdtProposalsAreOrderedLike(proposals: (String, String)*): Unit = {
    val sortedProposals = proposals.reverse.sortBy { case (prefix, name) =>
      -calc.forJdtType(prefix, name)
    }

    assertEquals(proposals, sortedProposals)
  }

  @Test
  def testIllustrativeJdtCompletions(): Unit = {
    makeSureThatJdtProposalsAreOrderedLike(
        "" -> "X",
        "z" -> "X",
        "a.a" -> "X")
  }

  @Test
  def testTypicalJdtUriCompletions(): Unit = {
    makeSureThatJdtProposalsAreOrderedLike(
        "java.net" -> "URI",
        "akka.http.scaladsl.model" -> "Uri",
        "gate.creole.ontology" -> "URI",
        "akka.http.javadsl.model" -> "Uri",
        "com.sun.jndi.toolkit.url" -> "Uri",
        "com.microsoft.schemas.office.x2006.encryption.CTKeyEncryptor" -> "Uri")
  }

  @Test
  def testTypicalJdtDocumentCompletions(): Unit = {
    makeSureThatJdtProposalsAreOrderedLike(
        "org.bson" -> "Document",
        "org.mongodb.scala.bson.collection.immutable" -> "Document",
        "gate.creole.annic.apache.lucene.document" -> "Document")
  }

}
