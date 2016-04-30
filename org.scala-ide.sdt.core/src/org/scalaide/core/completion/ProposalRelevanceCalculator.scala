package org.scalaide.core.completion

import scala.tools.nsc.interactive.Global
import org.scalaide.logging.HasLogger
import scala.util.matching.Regex

class ProposalRelevanceCalculator(cfg: ProposalRelevanceCfg = DefaultProposalRelevanceCfg) extends HasLogger {
  def forScala[CompilerT <: Global](pc: CompilerT)(prefix: String, name: String, sym: pc.Symbol, viaView: pc.Symbol, inherited: Option[Boolean]): Int = {
    // rudimentary relevance, place own members before inherited ones, and before view-provided ones
    var relevance = 1000
    if (!sym.isLocalToBlock) relevance -= 10 // non-local symbols are less relevant than local ones
    if (!sym.hasGetter) relevance -= 5 // fields are more relevant than non-fields
    if (inherited.exists(_ == true)) relevance -= 10
    if (viaView != pc.NoSymbol) relevance -= 20
    if (sym.hasPackageFlag) relevance -= 30
    // theoretically we'd need an 'ask' around this code, but given that
    // Any and AnyRef are definitely loaded, we call directly to definitions.
    if (sym.owner == pc.definitions.AnyClass
      || sym.owner == pc.definitions.AnyRefClass
      || sym.owner == pc.definitions.ObjectClass) {
      relevance -= 40
    }

    // global symbols are less relevant than local symbols
    sym.owner.enclosingPackage.fullName match {
      case "java" => relevance -= 15
      case "scala"  => relevance -= 10
      case _ =>
    }

    val casePenalty = if (name.substring(0, prefix.length) != prefix) 50 else 0
    relevance -= casePenalty

    relevance
  }

  def forJdtType(prefix: String, name: String): Int = {
    val maxRelevance = 500

    def deltaForPrefix(deltaIfMatch: Int, regexes: Seq[Regex]): Int = {
      regexes.foldLeft(0) { (acc, rx) =>
        prefix match {
          case `rx`() => acc + deltaIfMatch
          case _ => acc
        }
      }
    }

    val nestingLevel = {
      if (prefix == "") {
        0
      } else {
        // Note that `backtick` identifiers are passed to this method
        // in encoded form, so the naive approach below is actually OK.
        1 + prefix.count(_ == '.')
      }
    }

    val bonus =
       deltaForPrefix(3, cfg.favoritePackages) +
       deltaForPrefix(1, cfg.preferedPackages)

    val penalty =
      deltaForPrefix(3, cfg.shunnedPackages) +
      deltaForPrefix(1, cfg.unpopularPackages) +
      name.length*3 +
      nestingLevel

    math.max(math.min(maxRelevance, maxRelevance + bonus - penalty), 0)
  }
}
