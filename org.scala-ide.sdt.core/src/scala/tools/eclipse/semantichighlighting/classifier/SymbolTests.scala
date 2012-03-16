package scala.tools.eclipse.semantichighlighting.classifier

import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes._
import scala.tools.nsc.util.RangePosition

trait SymbolTests { self: SymbolClassification =>

  import global._

  def posToSym(pos: Position): Option[Symbol] = {
    val t = locateTree(pos) 
    if (t.hasSymbol) safeSymbol(t).headOption.map(_._1) else None
  }
  
  private lazy val forValSymbols: Set[Symbol] = for {
    region <- syntacticInfo.forVals
    symbol <- posToSym(rangePosition(region))
  } yield symbol

  private def rangePosition(region: Region): RangePosition = {
    val Region(offset, length) = region
    new RangePosition(sourceFile, offset, offset, offset + length - 1)
  }

  private def classifyTerm(sym: Symbol): SymbolType = {
    import sym._
    if (isPackage)
      Package
    else if (isLazy)
      if (isLocal) LazyLocalVal else LazyTemplateVal
    else if (isSetter)
      TemplateVar
    else if (isGetter && accessed.isMutable)
      TemplateVar
    else if (isGetter)
      TemplateVal
    else if (isSourceMethod)
      Method
    else if (isModule) {
      if (companionClass.isCaseClass)
        CaseClass
      else if (isCase)
        CaseObject
      else if (isJavaDefined)
        Class
      else
        Object
    } else if (isValue) {
      if (isLocal) {
        if (isVariable)
          LocalVar
        else if (isParameter && !forValSymbols.contains(sym))
          Param
        else if (isLazy)
          LazyLocalVal
        else
          LocalVal
      } else {
        if (isLazy)
          LazyTemplateVal
        else if (isVariable)
          TemplateVar
        else
          TemplateVal
      }
    } else
      throw new AssertionError("Unknown symbol type: " + sym)
  }

  private def classifyType(sym: Symbol): SymbolType = {
    import sym._
    if (isTrait)
      Trait
    else if (isCaseClass)
      CaseClass
    else if (isClass)
      Class
    else if (isParameter) // isTypeParam?
      TypeParameter
    else
      Type
  }

  def getSymbolType(sym: Symbol): SymbolType = {
    import sym._
    if (sym == NoSymbol)
      LocalVal
    else if (isTerm)
      classifyTerm(sym)
    else if (isType)
      classifyType(sym)
    else
      throw new AssertionError("Unknown symbol type: " + sym)
  }

}