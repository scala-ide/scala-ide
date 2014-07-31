package org.scalaide.core.internal.decorators.semantichighlighting.classifier

import org.scalaide.core.internal.decorators.semantichighlighting.classifier.SymbolTypes._
import scala.reflect.internal.util.RangePosition
import org.eclipse.jface.text.IRegion
import scala.reflect.internal.util.SourceFile

private[classifier] trait SymbolTests { self: SymbolClassification =>
  import SymbolTests._
  import global._

  def posToSym(pos: Position): Option[Symbol] = {
    val t = locateTree(pos)
    if (t.hasSymbolField) safeSymbol(t).headOption.map(_._1) else None
  }

  private lazy val forValSymbols: Set[Symbol] = for {
    region <- syntacticInfo.forVals
    pos = region.toRangePosition(sourceFile)
    symbol <- posToSym(pos)
  } yield symbol

  private def classifyTerm(sym: Symbol): SymbolType = {

    lazy val isCaseModule =
      global.askOption(() => sym.companionClass.isCaseClass).getOrElse(false)

    import sym._
    if (hasPackageFlag)
      Package
    else if (isLazy)
      if (isLocalToBlock) LazyLocalVal else LazyTemplateVal
    else if (isSetter)
      TemplateVar
    else if (isGetter)
      if (hasSetter(sym)) TemplateVar else TemplateVal
    else if (isSourceMethod)
      Method
    else if (isModule) {
      if (isCaseModule)
        CaseClass
      else if (isCase)
        CaseObject
      else if (isJavaDefined)
        Class
      else
        Object
    } else if (isValue) {
      if (isLocalToBlock) {
        if (isVariable)
          LocalVar
        else if (isValueParameter && !forValSymbols.contains(sym))
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
        else if (!hasGetter && !isThisSym && !isJavaDefined)
          // Short explanations of the above condition
          // hasGetter     -> must be a TemplateVal
          // isThisSym     -> self ref are categorized as TemplateVal
          // isJavaDefined -> fields in Java do not have default getters, but we still want to categorize them as TemplateVal
          Param
        else
          TemplateVal
      }
    } else
      throw new AssertionError("Unknown symbol type: " + sym)
  }

  /** Check if a setter exists for the passed getter {{{sym}}}.
    * @precondition sym.isGetter
    */
  private def hasSetter(sym: Symbol): Boolean = {
    assert(sym.isGetter)
    global.askOption(() => sym.setterIn(sym.owner) != NoSymbol).getOrElse(false)
  }

  private def classifyType(sym: Symbol): SymbolType = {
    import sym._
    if (isTrait)
      Trait
    else if (isCaseClass)
      CaseClass
    else if (isClass)
      Class
    else if (isTypeParameter || isTypeSkolem)
      TypeParameter
    else // isTypeAlias?
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

private object SymbolTests {
  private implicit class RegionOps(val __region: IRegion) extends AnyVal {
    def toRangePosition(sourceFile: SourceFile): RangePosition = {
      val offset = __region.getOffset
      val length = __region.getLength
      new RangePosition(sourceFile, offset, offset, offset + length - 1)
    }
  }
}
