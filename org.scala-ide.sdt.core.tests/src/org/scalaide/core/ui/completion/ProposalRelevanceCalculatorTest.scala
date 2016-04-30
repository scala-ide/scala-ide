package org.scalaide.core.ui.completion

import org.junit.Test
import org.junit.Assert._
import org.scalaide.core.completion.ProposalRelevanceCalculator
import org.scalaide.core.completion.ProposalRelevanceCfg
import org.scalaide.core.completion.DefaultProposalRelevanceCfg

class ProposalRelevanceCalculatorTest {
  private def makeSureThatJdtProposalsAreOrderedLike(cfg: ProposalRelevanceCfg = DefaultProposalRelevanceCfg)(proposals: (String, String)*): Unit = {
    val calc = new ProposalRelevanceCalculator(cfg)

    val sortedProposals = proposals.reverse.sortBy { case (prefix, name) =>
      -calc.forJdtType(prefix, name)
    }

    assertEquals(proposals, sortedProposals)
  }

  @Test
  def testIllustrativeJdtCompletions(): Unit = {
    makeSureThatJdtProposalsAreOrderedLike()(
        "" -> "X",
        "z" -> "X",
        "a.a" -> "X")
  }

  @Test
  def testTypicalJdtUriCompletions(): Unit = {
    makeSureThatJdtProposalsAreOrderedLike()(
        "java.net" -> "URI",
        "akka.http.scaladsl.model" -> "Uri",
        "gate.creole.ontology" -> "URI",
        "com.sun.jndi.toolkit.url" -> "Uri",
        "akka.http.javadsl.model" -> "Uri",
        "com.microsoft.schemas.office.x2006.encryption.CTKeyEncryptor" -> "Uri")
  }

  @Test
  def testTypicalJdtDocumentCompletions(): Unit = {
    makeSureThatJdtProposalsAreOrderedLike()(
        "org.bson" -> "Document",
        "org.mongodb.scala.bson.collection.immutable" -> "Document",
        "gate.creole.annic.apache.lucene.document" -> "Document")
  }

  @Test
  def testWithTypicalJsonCompletion(): Unit = {
    makeSureThatJdtProposalsAreOrderedLike()(
        "play.api.libs" -> "Json",
        "com.mongodb.util" -> "Json",
        "org.apache.avro.data" -> "Json",
        "play.libs" -> "Json")
  }

  @Test
  def testWithArtificialExampleAndCfg(): Unit = {
    val cfg = new ProposalRelevanceCfg {
      def favoritePackages =
        """.*favorite.*""".r :: Nil

      def preferedPackages =
        """.*prefered.*""".r :: Nil

      def unpopularPackages =
        """.*unpopular.*""".r :: Nil

      def shunnedPackages =
        """.*shunned.*""".r :: Nil
    }

    makeSureThatJdtProposalsAreOrderedLike(cfg)(
        "z.favorite" -> "A",
        "y.prefered" -> "A",
        "x.unpopular" -> "A",
        "w.shunned" -> "A",
        "z.favorite" -> "Aaaa",
        "y.prefered" -> "Aaaa",
        "x.unpopular" -> "Aaaa",
        "w.shunned" -> "Aaaa")
  }
}
