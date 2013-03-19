package scala.tools.eclipse.semantichighlighting.classifier

import org.eclipse.jface.text.IRegion

case class SymbolInfo(symbolType: SymbolTypes.SymbolType, regions: List[IRegion], deprecated: Boolean, inInterpolatedString: Boolean)


object SymbolTypes extends Enumeration {
  type SymbolType = Value
  
  val Annotation, CaseClass, CaseObject, Class , LazyLocalVal, 
      LazyTemplateVal , LocalVar, LocalVal, Method, Param, Object, 
      Package, TemplateVar, TemplateVal, Trait, Type, TypeParameter = Value
      
  private val Variables = Set(LazyLocalVal, LazyTemplateVal, LocalVar, LocalVal, Param, TemplateVar, TemplateVal)
  
  def isVariable(symbolType: SymbolType): Boolean = SymbolTypes.Variables.contains(symbolType)
}
