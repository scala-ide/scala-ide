package scala.tools.eclipse.semantichighlighting.classifier

case class SymbolInfo(symbolType: SymbolType, regions: List[Region], deprecated: Boolean)

sealed trait SymbolType

object SymbolTypes {

  case object Annotation extends SymbolType
  case object CaseClass extends SymbolType
  case object CaseObject extends SymbolType
  case object Class extends SymbolType
  case object LazyLocalVal extends SymbolType
  case object LazyTemplateVal extends SymbolType
  case object LocalVar extends SymbolType
  case object LocalVal extends SymbolType
  case object Method extends SymbolType
  case object Param extends SymbolType
  case object Object extends SymbolType
  case object Package extends SymbolType
  case object TemplateVar extends SymbolType
  case object TemplateVal extends SymbolType
  case object Trait extends SymbolType
  case object Type extends SymbolType
  case object TypeParameter extends SymbolType

}
