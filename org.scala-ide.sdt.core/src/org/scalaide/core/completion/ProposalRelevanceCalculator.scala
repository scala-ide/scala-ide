package org.scalaide.core.completion

import scala.tools.nsc.interactive.Global
import org.scalaide.logging.HasLogger

class ProposalRelevanceCalculator extends HasLogger {
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

    val boni = {
        if (prefix.contains(".scala")) 1
        else 0
      } :: {
        if (prefix.contains("akka.")) 1
        else 0
      } :: {
        if (prefix.startsWith("java.")) 1
        else if (prefix.startsWith("scala.")) 2
        else 0
      } :: Nil

    val penalties = {
        if (prefix.contains(".javadsl")) 1
        else 0
      } :: {
        name.length
      } :: {
        if (prefix == "") 0
        else prefix.count(_ == '.') + 1
      } :: Nil


    val bonus = boni.sum
    val penalty = penalties.sum

    math.max(math.min(maxRelevance, maxRelevance + bonus - penalty), 0)
  }
}
