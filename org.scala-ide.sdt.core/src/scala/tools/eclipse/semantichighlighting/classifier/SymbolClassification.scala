package scala.tools.eclipse.semantichighlighting.classifier

import scala.PartialFunction.condOpt
import scala.collection.mutable
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.Annotation
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.CaseClass
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.CaseObject
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.Class
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.LazyLocalVal
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.LazyTemplateVal
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.LocalVal
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.LocalVar
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.Method
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.Object
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.Param
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.TemplateVal
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.TemplateVar
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes.Type
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.RangePosition
import scala.tools.nsc.util.SourceFile
import scala.tools.refactoring.common.CompilerAccess
import scala.tools.refactoring.common.PimpedTrees


object SymbolClassification {

  /**
   *  If a symbol gets classified as more than one type, we give certain types precedence.
   *  Preference is given to map values over the corresponding key.
   */
  val pruneTable: Map[SymbolType, Set[SymbolType]] = Map(
    LocalVar -> Set(LazyLocalVal),
    LocalVal -> Set(LocalVar, TemplateVal, TemplateVar, LazyTemplateVal, LazyLocalVal),
    Method -> Set(TemplateVal, TemplateVar, LazyTemplateVal),
    Class -> Set(Type, CaseClass, Object),
    CaseClass -> Set(CaseObject),
    Param -> Set(TemplateVal, TemplateVar, LazyTemplateVal),
    TemplateVal -> Set(Type),
    Object -> Set(CaseClass))

  val debug = false
}

class SymbolClassification(protected val sourceFile: SourceFile, val global: ScalaPresentationCompiler, useSyntacticHints: Boolean)
  extends CompilerAccess with PimpedTrees with SymbolClassificationDebugger with SymbolTests with HasLogger {

  import SymbolClassification._
  import global._

  
  def compilationUnitOfFile(f: AbstractFile) = global.unitOfFile.get(f)

  protected val syntacticInfo =
    if (useSyntacticHints) SyntacticInfo.getSyntacticInfo(sourceFile.content.mkString) else SyntacticInfo.noSyntacticInfo

  lazy val unitTree = global.loadedType(sourceFile)

  /** Return the Symbols corresponding to this `Tree`, if any.
   * 
   *  Scala trees contain symbols when they define or reference a definition. We
   *  need to special-case `TypeTree`, because it does not return `true` on `hasSymbol`,
   *  but nevertheless it may refer to a symbol through its type. Examples of `TypeTree`s
   *  are the type of `ValDef`s after type checking.
   *  
   *  Another special case is `Import`, because it's selectors are not trees, therefore
   *  they do not have a symbol. However, it is desirable to color them, so the symbols are
   *  looked up on the fly.
   *  
   *  A single tree may define more than one symbol, usually with the same name. For instance:
   *    - a 'val' defines both a private field and a getter
   *    - a 'var' defines a private field, and getter/setter methods
   *    - a class parameter defines a constructor parameter, possibly a field and a getter
   *    - Import will generate one per selector
   * 
   *  Lazy values are special-cased because the underlying local var has a different
   *  name and there are no trees for the getter yet (added by phase lazyvals). We need
   *  to return the accessor, who can later be classified as `lazy`.
   */
  def safeSymbol(t: Tree): List[(Symbol, Position)] = {
    val syms = t match {
      case tpeTree: TypeTree =>
        val originalSym = safeSymbol(tpeTree.original)

        // if the original tree did not find anything, we need to call
        // symbol, which may trigger type checking of the underlying tree, so we
        // wrap it in 'ask'
        if (originalSym.isEmpty) {
          val tpeSym = global.askOption(() => Option(t.symbol)).flatten.toList
          tpeSym.zip(List(tpeTree.namePosition))
        } else originalSym

      case Import(expr, selectors) =>
        (for (ImportSelector(name, namePos, _, _) <- selectors) yield {
          // create a range position for this selector.
          // TODO: remove special casing once scalac is fixed, and ImportSelectors are proper trees,
          // with real positions, instead of just an Int
          val pos = rangePos(sourceFile, namePos, namePos, namePos + name.length)

          val sym1 = global.askOption(() => expr.tpe.member(name)).getOrElse(NoSymbol)
          if (sym1 eq NoSymbol) List()
          else if (sym1.isOverloaded) sym1.alternatives.take(1).zip(List(pos))
          else List((sym1, pos))
        }).flatten

      case _ =>
        // the local variable backing a lazy value is called 'originalName$lzy'. We swap it here for its
        // accessor, otherwise this symbol would fail the test in `getNameRegion`
        val sym1 = Option(t.symbol).map(sym => if (sym.isLazy && sym.isMutable) sym.lazyAccessor else sym).toList
        
        sym1.zip(t.namePosition :: Nil)
    }
    
    syms
  }

  def classifySymbols: List[SymbolInfo] = {
    // index.allSymbols will call `Symbol.initialize` on each symbol, hence it has to be executed inside an `ask`
    val allSymbols: List[(Symbol, Position)] = {
      for {
        t <- unitTree
        if (t.hasSymbol || t.isType) && t.pos.isRange && !t.pos.isTransparent
        (sym, pos) <- safeSymbol(t)
        if !sym.isSynthetic && !sym.isAnonymousClass
      } yield (sym, pos)
    }
    
    if (debug) printSymbolInfo()

    val rawSymbolInfos: Seq[SymbolInfo] = {
      val symAndPos = mutable.HashMap[Symbol, List[Position]]()
      for {
        (sym, pos) <- allSymbols
        if sym != NoSymbol
      } symAndPos(sym) = pos :: symAndPos.getOrElse(sym, Nil) 
      
      
      (for {
        (sym, poss) <- symAndPos
      } yield getSymbolInfo(sym, poss)).toStream
    }
    
    val prunedSymbolInfos = prune(rawSymbolInfos)
    val all: Set[Region] = rawSymbolInfos flatMap (_.regions) toSet
    val localVars: Set[Region] = rawSymbolInfos.collect { case SymbolInfo(LocalVar, regions, _) => regions }.flatten.toSet
    val symbolInfosFromSyntax = getSymbolInfosFromSyntax(syntacticInfo, localVars, all)

    val res = (symbolInfosFromSyntax ++ prunedSymbolInfos) filter { _.regions.nonEmpty } distinct
    
    logger.debug("raw symbols: %d, pruned symbols: %d".format(rawSymbolInfos.size, prunedSymbolInfos.size))
    res
  }

  private def getSymbolInfo(sym: Symbol, poss: List[Position]): SymbolInfo = {
    val regions = poss.flatMap(getOccurrenceRegion(sym)).toList
    // isDeprecated may trigger type completion for annotations
    val deprecated = sym.annotations.nonEmpty && global.askOption(() => sym.isDeprecated).getOrElse(false)
    SymbolInfo(getSymbolType(sym), regions, deprecated)
  }

  private def getOccurrenceRegion(sym: Symbol)(pos: Position): Option[Region] =
    getNameRegion(pos) flatMap { region =>
      val text = region of sourceFile.content
      val symName = sym.nameString
      if (symName.startsWith(text) || text == "`" + symName + "`")
        Some(region)
      else {
        logger.debug("couldn't find region for: " + sym + " at: " + pos.line)
        None
      }
    }

  private def getNameRegion(pos: Position): Option[Region] =
    try
      condOpt(pos) {
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

  private def prune(rawSymbolInfos: Seq[SymbolInfo]): Seq[SymbolInfo] = {
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