package org.scalaide.core.completion

import scala.util.matching.Regex

trait ProposalRelevanceCfg {
  def favoritePackages: Seq[Regex]
  def preferredPackages: Seq[Regex]

  def unpopularPackages: Seq[Regex]
  def shunnedPackages: Seq[Regex]

  override def toString = {
    s"ProposalRelevanceCfg[favoritePackages=$favoritePackages, preferredPackages=$preferredPackages,unpopularPackages=$unpopularPackages,shunnedPackages=$shunnedPackages]"
  }
}

object DefaultProposalRelevanceCfg extends ProposalRelevanceCfg {
  val favoritePackages =
    """scala\..*""".r ::
    Nil

  val preferredPackages =
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
