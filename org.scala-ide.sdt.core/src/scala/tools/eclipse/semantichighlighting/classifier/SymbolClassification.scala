package scala.tools.eclipse.semantichighlighting.classifier

import scala.tools.nsc.interactive.Global
import scala.tools.nsc.util.SourceFile
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.common.Selections
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes._
import scala.PartialFunction.{ cond, condOpt }
import scala.tools.nsc.util.BatchSourceFile
import scala.tools.nsc.util.RangePosition
import scalariform.parser.ScalaParser
import scalariform.lexer.ScalaLexer
import scalariform.parser.{ Type => _, Param => _, Annotation => _, _ }
import scalariform.parser.Argument
import scalariform.utils.Utils.time
import scala.tools.refactoring.util.TreeCreationMethods

object SymbolClassification {

  /**
   *  If a symbol gets classified as more than one type, we give certain types precedence.
   *  Preference is given to map values over the corresponding key.
   */
  val pruneTable: Map[SymbolType, Set[SymbolType]] = Map(
    LocalVal -> Set(LocalVar, TemplateVal, TemplateVar, LazyTemplateVal, LazyLocalVal),
    Method -> Set(TemplateVal, TemplateVar, LazyTemplateVal),
    Class -> Set(Type, CaseClass, Object),
    CaseClass -> Set(CaseObject),
    Param -> Set(TemplateVal, TemplateVar, LazyTemplateVal),
    TemplateVal -> Set(Type),
    Object -> Set(CaseClass))

  val debug = false
}

class SymbolClassification(protected val sourceFile: SourceFile, val global: Global, useSyntacticHints: Boolean)
  extends Selections with GlobalIndexes with SymbolClassificationDebugger with SymbolTests with TreeCreationMethods {

  import SymbolClassification._
  import global._

  protected val source = sourceFile.content.mkString

  protected val syntacticInfo =
    if (useSyntacticHints) SyntacticInfo.getSyntacticInfo(source) else SyntacticInfo.noSyntacticInfo

  protected val tree = treeFrom(sourceFile)
  val index = GlobalIndex(tree)

  def classifySymbols: List[SymbolInfo] = {
    val allSymbols = index.allSymbols
    allSymbols foreach (_.initialize) // some symbol properties (e.g. _.isJavaDefined) appear to be able to change after init

    if (debug)
      printSymbolInfo()

    val rawSymbolInfos: List[SymbolInfo] = allSymbols map getSymbolInfo
    val prunedSymbolInfos = prune(rawSymbolInfos)
    val all: Set[Region] = rawSymbolInfos flatMap (_.regions) toSet
    val localVars: Set[Region] = rawSymbolInfos.collect { case SymbolInfo(LocalVar, regions, _) => regions }.flatten.toSet
    val symbolInfosFromSyntax = getSymbolInfosFromSyntax(syntacticInfo, localVars, all)

    (symbolInfosFromSyntax ++ prunedSymbolInfos) filter { _.regions.nonEmpty } distinct
  }

  private def getSymbolInfo(sym: Symbol): SymbolInfo = {
    val regions = index occurences sym flatMap getOccurrenceRegion(sym)
    SymbolInfo(getSymbolType(sym), regions, sym.isDeprecated)
  }

  private def getOccurrenceRegion(sym: Symbol)(occurrence: Tree): Option[Region] =
    getNameRegion(occurrence) flatMap { region =>
      val text = region of source
      val symName = sym.nameString
      if (text == symName || text == "`" + symName + "`")
        Some(region)
      else
        None
    }

  private def getNameRegion(tree: Tree): Option[Region] =
    try
      condOpt(tree.namePosition) {
        case rangePosition: RangePosition => Region(rangePosition.start, rangePosition.end - rangePosition.start)
      }
    catch {
      case e => None
    }

  private def getSymbolInfosFromSyntax(syntacticInfo: SyntacticInfo, localVars: Set[Region], all: Set[Region]): List[SymbolInfo] = {
    val SyntacticInfo(namedArgs, forVals, maybeSelfRefs, maybeClassOfs, annotations) = syntacticInfo
    List(
      SymbolInfo(LocalVal, forVals toList, deprecated = false),
      SymbolInfo(Param, namedArgs filterNot localVars toList, deprecated = false),
      SymbolInfo(TemplateVal, maybeSelfRefs filterNot all toList, deprecated = false),
      SymbolInfo(Method, maybeClassOfs filterNot all toList, deprecated = false),
      SymbolInfo(Annotation, annotations filterNot all toList, deprecated = false))
  }

  private def prune(rawSymbolInfos: List[SymbolInfo]): List[SymbolInfo] = {
    def findRegionsWithSymbolType(symbolType: SymbolType): Set[Region] =
      rawSymbolInfos.collect { case SymbolInfo(`symbolType`, regions, _) => regions }.flatten.toSet

    val symbolTypeToRegion: Map[SymbolType, Set[Region]] = pruneTable mapValues (_ flatMap findRegionsWithSymbolType)

    def pruneMisidentifiedSymbols(symbolInfo: SymbolInfo): SymbolInfo =
      symbolTypeToRegion.get(symbolInfo.symbolType) match {
        case Some(regionsToRemove) => symbolInfo.copy(regions = symbolInfo.regions filterNot regionsToRemove)
        case None => symbolInfo
      }

    rawSymbolInfos.map(pruneMisidentifiedSymbols)
  }

}