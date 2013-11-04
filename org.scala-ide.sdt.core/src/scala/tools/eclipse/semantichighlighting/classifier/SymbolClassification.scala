package scala.tools.eclipse.semantichighlighting.classifier
import scala.PartialFunction.condOpt
import scala.collection.mutable
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes._
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.RangePosition
import scala.reflect.internal.util.SourceFile
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.Region
import scala.tools.eclipse.jface.text.RegionOps._

private object SymbolClassification {
  private val debug = false

  /**
   *  If a symbol gets classified as more than one type, we give certain types precedence.
   *  Preference is given to map values over the corresponding key.
   */
  private val pruneTable: Map[SymbolType, Set[SymbolType]] = Map(
    LocalVar -> Set(LazyLocalVal),
    LocalVal -> Set(LocalVar, TemplateVal, TemplateVar, LazyTemplateVal, LazyLocalVal),
    Method -> Set(TemplateVal, TemplateVar, LazyTemplateVal),
    Class -> Set(Type, CaseClass, Object),
    CaseClass -> Set(CaseObject),
    Param -> Set(TemplateVal, TemplateVar, LazyTemplateVal),
    TemplateVal -> Set(Type),
    Object -> Set(CaseClass)
  )


}

class SymbolClassification(protected val sourceFile: SourceFile, val global: ScalaPresentationCompiler, useSyntacticHints: Boolean)
  extends SafeSymbol with TypeTreeTraverser with SymbolClassificationDebugger with SymbolTests with HasLogger {

  import SymbolClassification._
  import global.Symbol
  import global.Position
  import global.NoSymbol

  def compilationUnitOfFile(f: AbstractFile) = global.unitOfFile.get(f)

  protected lazy val syntacticInfo =
    if (useSyntacticHints) SyntacticInfo.getSyntacticInfo(sourceFile.content.mkString) else SyntacticInfo.noSyntacticInfo

  private lazy val unitTree: global.Tree = global.loadedType(sourceFile, true).fold(identity, _ => global.EmptyTree)

  private def canSymbolBeReferencedInSource(sym: Symbol): Boolean = {
    def isSyntheticMethodParam(sym: Symbol): Boolean = sym.isSynthetic && sym.isValueParameter

    !sym.isAnonymousFunction &&
    !sym.isAnonymousClass &&
    !isSyntheticMethodParam(sym)
  }

  def classifySymbols(progressMonitor: IProgressMonitor): List[SymbolInfo] = {
    if(progressMonitor.isCanceled()) return Nil

    val allSymbols: List[(Symbol, Position)] = {
      for {
        t <- unitTree
        if !progressMonitor.isCanceled() && (t.hasSymbol || t.isType) && isSourceTree(t)
        (sym, pos) <- safeSymbol(t)
        if canSymbolBeReferencedInSource(sym)
      } yield (sym, pos)
    }

    if(progressMonitor.isCanceled()) return Nil

    if (debug) printSymbolInfo()

    val rawSymbolInfos: Seq[SymbolInfo] = {
      case class SymbolGroup(symbol: Symbol, inInterpolatedString: Boolean)
      val symAndPos = mutable.HashMap[SymbolGroup, List[Position]]().withDefaultValue(Nil)
      for {
        (sym, pos) <- allSymbols
        if sym != NoSymbol
      } {
        val inInterpolatedString = syntacticInfo.identifiersInStringInterpolations.contains(getOccurrenceRegion(sym)(pos).orNull)
        symAndPos(SymbolGroup(sym, inInterpolatedString)) = pos :: symAndPos(SymbolGroup(sym, inInterpolatedString))
      }

      if (progressMonitor.isCanceled()) Nil
      else {
        (for {
          (SymbolGroup(sym,inInterpolatedString), poss) <- symAndPos
        } yield getSymbolInfo(sym, poss, inInterpolatedString)).toList
      }
    }

    if (progressMonitor.isCanceled()) return Nil

    val prunedSymbolInfos = prune(rawSymbolInfos)
    if (progressMonitor.isCanceled()) return Nil

    val all: Set[IRegion] = rawSymbolInfos.flatMap(_.regions).toSet
    if (progressMonitor.isCanceled()) return Nil

    val localVars: Set[IRegion] = rawSymbolInfos.collect { case SymbolInfo(LocalVar, regions, _, _) => regions }.flatten.toSet
    if (progressMonitor.isCanceled()) return Nil

    val symbolInfosFromSyntax = getSymbolInfosFromSyntax(syntacticInfo, localVars, all)
    if (progressMonitor.isCanceled()) return Nil

    (symbolInfosFromSyntax ++ prunedSymbolInfos).filter(_.regions.nonEmpty).distinct
  }

  private def getSymbolInfo(sym: Symbol, poss: List[Position], inInterpolatedString: Boolean): SymbolInfo = {
    val regions = poss.flatMap(getOccurrenceRegion(sym)).toList
    // isDeprecated may trigger type completion for annotations
    val deprecated = sym.annotations.nonEmpty && global.askOption(() => sym.isDeprecated).getOrElse(false)
    val symbolType = getSymbolType(sym)
    SymbolInfo(symbolType, regions, deprecated, inInterpolatedString)
  }

  private def getOccurrenceRegion(sym: Symbol)(pos: Position): Option[IRegion] =
    getNameRegion(pos) flatMap { region =>
      val text = region of sourceFile.content
      val symName = sym.nameString
      if (symName.startsWith(text) || text == "`" + symName + "`") Some(region)
      else None
    }

  private def getNameRegion(pos: Position): Option[IRegion] =
    try
      condOpt(pos) {
        case rangePosition: RangePosition => new Region(rangePosition.start, rangePosition.end - rangePosition.start)
      }
    catch {
      case e: Exception => None
    }

  private def getSymbolInfosFromSyntax(syntacticInfo: SyntacticInfo, localVars: Set[IRegion], all: Set[IRegion]): List[SymbolInfo] = {
    val SyntacticInfo(namedArgs, forVals, maybeSelfRefs, maybeClassOfs, annotations, packages, _) = syntacticInfo
    List(
      SymbolInfo(LocalVal, forVals.toList, deprecated = false, inInterpolatedString = false),
      SymbolInfo(Param, namedArgs.filterNot(localVars).toList, deprecated = false, inInterpolatedString = false),
      SymbolInfo(TemplateVal, maybeSelfRefs.filterNot(all).toList, deprecated = false, inInterpolatedString = false),
      SymbolInfo(Method, maybeClassOfs.filterNot(all).toList, deprecated = false, inInterpolatedString = false),
      SymbolInfo(Annotation, annotations.filterNot(all).toList, deprecated = false, inInterpolatedString = false),
      SymbolInfo(Package, packages.filterNot(all).toList, deprecated = false, inInterpolatedString = false))
  }

  private def prune(rawSymbolInfos: Seq[SymbolInfo]): Seq[SymbolInfo] = {
    def findRegionsWithSymbolType(symbolType: SymbolType): Set[IRegion] =
      rawSymbolInfos.collect { case SymbolInfo(`symbolType`, regions, _, _) => regions }.flatten.toSet

    val symbolTypeToRegion: Map[SymbolType, Set[IRegion]] = {
      // we use `map' instead of the more elegant `mapValue(f)` because the latter is
      // a `view': it applies `f' for each retrieved key, wihtout any caching. This
      // causes quadratic behavior: `findRegionsWithSymbolType` is linear in rawSymbolInfos,
      // and this map is called for each symbol in rawSymbolInfos
      // `map' is strict, and creates a new result map that leads to much better runtime behavior.
      pruneTable map {
        case (symType, regions) => (symType, regions flatMap findRegionsWithSymbolType)
      }
    }

    def pruneMisidentifiedSymbols(symbolInfo: SymbolInfo): SymbolInfo =
      symbolTypeToRegion.get(symbolInfo.symbolType) match {
        case Some(regionsToRemove) => symbolInfo.copy(regions = symbolInfo.regions filterNot regionsToRemove)
        case None => symbolInfo
      }

    rawSymbolInfos.map(pruneMisidentifiedSymbols)
  }

}