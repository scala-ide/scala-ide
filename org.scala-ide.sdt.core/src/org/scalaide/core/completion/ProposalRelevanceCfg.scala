package org.scalaide.core.completion

import scala.util.matching.Regex

trait ProposalRelevanceCfg {
  def favoritePackages: Seq[Regex]
  def preferedPackages: Seq[Regex]

  def unpopularPackages: Seq[Regex]
  def shunnedPackages: Seq[Regex]
}

object DefaultProposalRelevanceCfg extends ProposalRelevanceCfg {
  val favoritePackages =
    """scala\..*""".r ::
    Nil

  val preferedPackages =
    """java\..*""".r ::
    """.*\.scala.*""".r ::
    """akka\..*""".r ::
    """play.api\..*""".r ::
    Nil

  val unpopularPackages = Nil

  val shunnedPackages =
    """.*\.javadsl.*""".r ::
    """play\.(?!api\.).*""".r ::
    Nil
}
