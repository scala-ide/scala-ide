/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.sources

import scala.reflect.internal.util.SourceFile

import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.logging.HasLogger

object Imports extends HasLogger with SPCIntegration {

  type ScopedImports = Seq[List[String]] // List for easier AST integration

  private val ImportsSearchTimeoutInMillis = 100

  private def reportProblemWithObtainingImports(reason: String): ScopedImports = {
    logger.warn(s"No imports due to: $reason")
    Nil
  }

  private case class ImportExtractor(pc: IScalaPresentationCompiler,
    toLineNumber: Int) {

    class ImportTraverser
      extends pc.Traverser
      with (pc.Tree => ScopedImports) {

      // store trees that create new scope
      private var path: Seq[pc.Tree] = Nil
      // imports per given scope
      private var importsMap: Map[Option[pc.Tree], List[pc.Import]] = Map().withDefault(_ => Nil)
      // set to true to stop traversing (for whole tree not just current subtree)
      private var done: Boolean = false

      override def traverse(tree: pc.Tree): Unit = {
        // Skip if already found in different subtree
        if (!done) {
          tree.pos match {
            case pc.NoPosition =>
            case pos if pos.line == toLineNumber =>
              //we reach desired line - end searching
              done = true
            case _ =>
          }
          // Check it again - required line might be found in previous block
          if (!done) {
            tree match {
              case _: pc.Function | _: pc.Block | _: pc.CaseDef =>
                //add new scope
                path = tree +: path
              case importTree: pc.Import =>
                //add new import to scope
                val currentScopeImports = importsMap(path.headOption) :+ importTree
                importsMap = importsMap + (path.headOption -> currentScopeImports)
              case _ =>
            }
            super.traverse(tree)
          }
        }
      }

      /** Stringify select using names instead of toString */
      private def stringifySelect(tree: pc.Tree): String = tree match {
        case pc.Ident(name) => name.toString
        case pc.Select(on, name) => stringifySelect(on) + "." + name
        case other => throw new RuntimeException(s"We don't support manual to string conversion for: $other")
      }

      /** Manually convert import to string */
      def manuallyStringifyImport(importTree: pc.Import) ={
        val originalRoot = stringifySelect(importTree.expr)

        // Selectors are hard to stringify.
        // Fake import is created just to obtain original stringified selectors
        val fakeRoot = pc.Ident(pc.TermName("fakeRoot"))
        val fakeImport = pc.Import(fakeRoot, importTree.selectors)
        val stringifiedSelectors = fakeImport.toString().drop("import fakeRoot.".size)

        s"import $originalRoot.$stringifiedSelectors"
      }

      /** Create string from tree. Works also with erroneous trees */
      def stringifyImport(importTree: pc.Import): String =
        if (importTree.expr.isErroneous) manuallyStringifyImport(importTree)
        else importTree.toString()

      override def apply(tree: pc.Tree): ScopedImports = {
        traverse(tree)
        val scopes = None +: path.map(Option.apply)
        scopes.flatMap(importsMap.get).map(_.map(stringifyImport))
      }
    }

    def importForFile(tree: pc.Tree): ScopedImports =
      new ImportTraverser().apply(tree)
  }

  private def getImports(pc: IScalaPresentationCompiler, sourceFile: SourceFile, line: Int):ScopedImports = {
    pc.askParsedEntered(sourceFile, true).get(ImportsSearchTimeoutInMillis) match {
      case None => reportProblemWithObtainingImports("timeout")
      case Some(Right(error)) => reportProblemWithObtainingImports(error.getMessage)
      case Some(Left(tree)) =>
        pc.asyncExec {
          val extractor = new ImportExtractor(pc, line)
          extractor.importForFile(tree.asInstanceOf[extractor.pc.Tree])
        }.get(ImportsSearchTimeoutInMillis) match {
          case None => reportProblemWithObtainingImports("timeout")
          case Some(Right(error)) => reportProblemWithObtainingImports(error.getMessage)
          case Some(Left(imports)) => imports
        }
    }
  }

  def importsFromCurrentStackFrame: ScopedImports = forCurrentStackFrame(getImports, reportProblemWithObtainingImports)
}