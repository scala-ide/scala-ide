package org.scalaide.core.completion

import scala.util.matching.Regex
import org.scalaide.ui.internal.preferences.StringListSerializer
import org.scalaide.core.IScalaPlugin

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

object PrefsBasedProposalRelevanceCfg extends ProposalRelevanceCfg {
  val PFavoritePackages = "scala.tools.eclipse.ui.preferences.completions.favoritePackages"
  val PPreferredPackages = "scala.tools.eclipse.ui.preferences.completions.preferredPackages"
  val PUnpopularPackages = "scala.tools.eclipse.ui.preferences.completions.unpopularPackages"
  val PShunnedPackages = "scala.tools.eclipse.ui.preferences.completions.shunnedPackages"

  private def prefStore = IScalaPlugin().getPreferenceStore
  private def strFromPrefStore(key: String) = prefStore.getString(key)

  private def rxSeqFromPrefStore(key: String) = {
    StringListSerializer.deserialize(strFromPrefStore(key)).map(_.r)
  }

  def favoritePackages = rxSeqFromPrefStore(PFavoritePackages)
  def preferredPackages = rxSeqFromPrefStore(PPreferredPackages)
  def unpopularPackages = rxSeqFromPrefStore(PUnpopularPackages)
  def shunnedPackages = rxSeqFromPrefStore(PShunnedPackages)
}
