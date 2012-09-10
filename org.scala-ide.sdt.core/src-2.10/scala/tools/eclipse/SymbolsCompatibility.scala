package scala.tools.eclipse

import scala.reflect.internal.Symbols

/**
 * Trait used to keep 2.9-2.10 source compatibility
 */
trait SymbolsCompatibility { self: Symbols =>

  /**
   * This class as been removed in 2.10, but we need its real implementation in 2.9
   */
  case class InvalidCompanions(sym1: Symbol, sym2: Symbol) extends Throwable {

  }

}
