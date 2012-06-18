package scala.tools.eclipse.javaelements

import scala.tools.eclipse.ScalaPresentationCompiler

object SymbolNameUtil {
  final val MODULE_SUFFIX_STRING = "$"
}

/** 
 * Contains methods for converting symbol's name to plain string values.
 * 
 * @note: The current implementation was extracted from 
 * [[https://github.com/scala/scala/blob/master/src/reflect/scala/reflect/internal/Symbols.scala#L995 Symbols.scala]]
 * (which is of course not available in 2.9.x). 
 */
trait SymbolNameUtil { self: ScalaPresentationCompiler =>
  import SymbolNameUtil.MODULE_SUFFIX_STRING
  /**
   * Whether this symbol needs nme.MODULE_SUFFIX_STRING (aka $) appended on the java platform.
   */
  private def needsModuleSuffix(sym: Symbol) = (
    sym.hasModuleFlag
    && !sym.isMethod
    && !sym.isImplClass
    && !sym.isJavaDefined)

  def javaSimpleName(sym: Symbol): String = addModuleSuffix(sym, sym.simpleName)
  def javaBinaryName(sym: Symbol): String = addModuleSuffix(sym, sym.fullName('/'))
  def javaClassName(sym: Symbol): String = addModuleSuffix(sym, sym.fullName('.'))

  // needed to compile against 2.9
  private def addModuleSuffix(sym: Symbol, n: Name): String =
    addModuleSuffix(sym, n.toString)
  
  // needed to compile against 2.10
  private def addModuleSuffix(sym: Symbol, n: String): String =
    if (needsModuleSuffix(sym)) n + MODULE_SUFFIX_STRING else n
}