package scala.tools.eclipse.semantichighlighting.classifier

import scala.tools.refactoring.common.CompilerAccess
import scala.tools.refactoring.common.PimpedTrees
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.nsc.util.SourceFile

/**
 * Return the Symbols corresponding to this `Tree`, if any.
 *
 * Scala trees contain symbols when they define or reference a definition. We
 * need to special-case `TypeTree`, because it does not return `true` on `hasSymbol`,
 * but nevertheless it may refer to a symbol through its type. Examples of `TypeTree`s
 * are the type of `ValDef`s after type checking.
 *
 * Another special case is `Import`, because it's selectors are not trees, therefore
 * they do not have a symbol. However, it is desirable to color them, so the symbols are
 * looked up on the fly.
 *
 * A single tree may define more than one symbol, usually with the same name. For instance:
 *   - a 'val' defines both a private field and a getter
 *   - a 'var' defines a private field, and getter/setter methods
 *   - a class parameter defines a constructor parameter, possibly a field and a getter
 *   - Import will generate one per selector
 *
 * Lazy values are special-cased because the underlying local var has a different
 * name and there are no trees for the getter yet (added by phase lazyvals). We need
 * to return the accessor, who can later be classified as `lazy`.
 */
trait SafeSymbol extends CompilerAccess with PimpedTrees {

  val global: ScalaPresentationCompiler

  protected def sourceFile: SourceFile

  import global._

  /**
   * Trees that have a direct correspondence in the source code have a RangePosition.
   * TransparentPositions come into play for trees that don't have a source-code
   * correspondence but still have children that are visible in the source.
   */
  protected def isSourceTree(t: Tree): Boolean = t.pos.isRange && !t.pos.isTransparent

  protected def safeSymbol(t: Tree): List[(Symbol, Position)] = t match {

    case tpeTree: TypeTree =>
      val originalSym =
        if ((tpeTree.original eq null) || tpeTree.original == tpeTree) Nil
        else safeSymbol(tpeTree.original)

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

        val sym1 = if (expr.tpe ne null) global.askOption { () =>
          val typeSym = expr.tpe.member(name.toTypeName)
          if (typeSym.exists) typeSym
          else expr.tpe.member(name.toTermName)
        }.getOrElse(NoSymbol)
        else NoSymbol
        
        if (sym1 eq NoSymbol) List()
        else if (sym1.isOverloaded) sym1.alternatives.take(1).zip(List(pos))
        else List((sym1, pos))
      }).flatten

    case AppliedTypeTree(tpe @ Select(qualifier, name), args) if isFunctionLiteral(tpe, qualifier, name) =>
      args.flatMap(safeSymbol)

    case AppliedTypeTree(tpe, args) if isContextBound(args) =>
      List(tpe.symbol -> tpe.namePosition)

    case AppliedTypeTree(tpe, args) =>
      tpe.symbol -> tpe.namePosition :: args.flatMap(safeSymbol)

    case CompoundTypeTree(tmpl) =>
      tmpl.parents.flatMap(safeSymbol)

    case _ =>
      // the local variable backing a lazy value is called 'originalName$lzy'. We swap it here for its
      // accessor, otherwise this symbol would fail the test in `getNameRegion`
      val sym1 = Option(t.symbol).map { sym =>
        if (sym.isLazy && sym.isMutable) sym.lazyAccessor
        else sym
      }.toList

      sym1.zip(List(t.namePosition))
  }

  private def isFunctionLiteral(tpt: Tree, qualifier: Tree, name: Name): Boolean = (
       qualifier.nameString == "scala"
    && name.toString.startsWith("Function")
    && !tpt.pos.isRange
  )

  private def isContextBound(args: List[Tree]): Boolean =
    args.size == 1 && !args.head.pos.isRange
}