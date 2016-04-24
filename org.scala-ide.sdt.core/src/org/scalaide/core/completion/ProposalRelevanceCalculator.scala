package org.scalaide.core.completion

import scala.tools.nsc.interactive.Global

class ProposalRelevanceCalculator {
  def forScala[CompilerT <: Global](pc: CompilerT)(prefix: String, name: String, sym: pc.Symbol, viaView: pc.Symbol, inherited: Option[Boolean]): Int = {
    // rudimentary relevance, place own members before inherited ones, and before view-provided ones
    var relevance = 100
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
    50
  }
}
