/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.proxies.phases {

  import scala.reflect.runtime.universe

  import org.scalaide.debug.internal.expression.AfterTypecheck
  import org.scalaide.debug.internal.expression.TransformationPhase
  import org.scalaide.debug.internal.expression.TransformationPhaseData

  /**
   * Removes all type information from given tree.
   *
   * This should work with just `toolbox.untypecheck`, but it did not work in our case.
   */
  class ResetTypeInformation extends TransformationPhase[AfterTypecheck] {

    override def transform(data: TransformationPhaseData): TransformationPhaseData = {
      import scala.tools.nsc.ast.Brutal
      val runtimeUniverse = universe.asInstanceOf[scala.reflect.runtime.JavaUniverse]
      val brutal = new Brutal(runtimeUniverse)
      val brutalTree = data.tree.asInstanceOf[brutal.u.Tree]
      val resetTree = brutal.brutallyResetAttrs(brutalTree)
      val newTree = resetTree.asInstanceOf[universe.Tree]
      data.after(phaseName, newTree)
    }
  }

}

package scala.tools.nsc.ast {

  /**
   * Copied from https://github.com/xeno-by/scala/blob/d079e769b9372daae8d7770c4156f85ea1af6621/src/compiler/scala/tools/nsc/ast/Trees.scala#L205
   * as a workaround for resetAllAttrs removal from ToolBox in Scala 2.11.
   */
  class Brutal(val u: scala.reflect.runtime.JavaUniverse) {

    import u._
    import scala.tools.nsc._
    import scala.compat.Platform.EOL

    def brutallyResetAttrs(x: Tree, leaveAlone: Tree => Boolean = null): Tree = new ResetAttrs(brutally = true, leaveAlone).transform(x)

    /**
     * A transformer which resets symbol and tpe fields of all nodes in a given tree,
     * with special treatment of:
     * TypeTree nodes: are replaced by their original if it exists, otherwise tpe field is reset
     * to empty if it started out empty or refers to local symbols (which are erased).
     * TypeApply nodes: are deleted if type arguments end up reverted to empty
     * This(pkg) nodes where pkg is a package: these are kept.
     *
     * (bq:) This transformer has mutable state and should be discarded after use
     */
    private class ResetAttrs(brutally: Boolean, leaveAlone: Tree => Boolean) {
      // this used to be based on -Ydebug, but the need for logging in this code is so situational
      // that I've reverted to a hard-coded constant here.
      val debug = false
      val trace = scala.tools.nsc.util.trace when debug

      val locals = util.HashSet[Symbol](8)
      val orderedLocals = scala.collection.mutable.ListBuffer[Symbol]()
      def registerLocal(sym: Symbol) {
        if (sym != null && sym != NoSymbol) {
          if (debug && !(locals contains sym)) orderedLocals append sym
          locals addEntry sym
        }
      }

      class MarkLocals extends Traverser {
        def markLocal(tree: Tree) {
          if (tree.symbol != null && tree.symbol != NoSymbol) {
            val sym = tree.symbol
            registerLocal(sym)
            registerLocal(sym.sourceModule)
            registerLocal(sym.moduleClass)
            registerLocal(sym.companionClass)
            registerLocal(sym.companionModule)
            registerLocal(sym.deSkolemize)
            sym match {
              case sym: TermSymbol => registerLocal(sym.referenced)
              case _ => ;
            }
          }
        }

        override def traverse(tree: Tree) = {
          tree match {
            case _: DefTree | Function(_, _) | Template(_, _, _) =>
              markLocal(tree)
            case _ =>
              tree
          }

          super.traverse(tree)
        }
      }

      class Transformer extends u.Transformer {
        override def transform(tree: Tree): Tree = {
          if (leaveAlone != null && leaveAlone(tree))
            tree
          else
            super.transform {
              tree match {
                case tree if !tree.canHaveAttrs =>
                  tree
                case tpt: TypeTree =>
                  if (tpt.original != null)
                    transform(tpt.original)
                  else {
                    val refersToLocalSymbols = tpt.tpe != null && (tpt.tpe exists (tp => locals contains tp.typeSymbol))
                    val isInferred = tpt.wasEmpty
                    if (refersToLocalSymbols || isInferred) {
                      tpt.duplicate.clearType()
                    } else {
                      tpt
                    }
                  }
                // If one of the type arguments of a TypeApply gets reset to an empty TypeTree, then this means that:
                // 1) It isn't empty now (tpt.tpe != null), but it was empty before (tpt.wasEmpty).
                // 2) Thus, its argument got inferred during a preceding typecheck.
                // 3) Thus, all its arguments were inferred (because scalac can only infer all or nothing).
                // Therefore, we can safely erase the TypeApply altogether and have it inferred once again in a subsequent typecheck.
                // UPD: Actually there's another reason for erasing a type behind the TypeTree
                // is when this type refers to symbols defined in the tree being processed.
                // These symbols will be erased, because we can't leave alive a type referring to them.
                // Here we can only hope that everything will work fine afterwards.
                case TypeApply(fn, args) if args map transform exists (_.isEmpty) =>
                  transform(fn)
                case EmptyTree =>
                  tree
                case _ =>
                  val dupl = tree.duplicate
                  // Typically the resetAttrs transformer cleans both symbols and types.
                  // However there are exceptions when we cannot erase symbols due to idiosyncrasies of the typer.
                  // vetoXXX local variables declared below describe the conditions under which we cannot erase symbols.
                  //
                  // The first reason to not erase symbols is the threat of non-idempotency (SI-5464).
                  // Here we take care of references to package classes (SI-5705).
                  // There are other non-idempotencies, but they are not worked around yet.
                  //
                  // The second reason has to do with the fact that resetAttrs needs to be less destructive.
                  // Erasing locally-defined symbols is useful to prevent tree corruption, but erasing external bindings is not,
                  // therefore we want to retain those bindings, especially given that restoring them can be impossible
                  // if we move these trees into lexical contexts different from their original locations.
                  if (dupl.hasSymbolField) {
                    val sym = dupl.symbol
                    val vetoScope = !brutally && !(locals contains sym) && !(locals contains sym.deSkolemize)
                    val vetoThis = dupl.isInstanceOf[This] && sym.isPackageClass
                    if (!(vetoScope || vetoThis)) dupl.symbol = NoSymbol
                  }
                  dupl.clearType()
              }
            }
        }
      }

      def transform(x: Tree): Tree = {
        new MarkLocals().traverse(x)

        if (debug) {
          assert(locals.size == orderedLocals.size)
          val msg = orderedLocals.toList filter { _ != NoSymbol } map { " " + _ } mkString EOL
          trace("locals (%d total): %n".format(orderedLocals.size))(msg)
        }

        new Transformer().transform(x)
      }
    }

  }

}
