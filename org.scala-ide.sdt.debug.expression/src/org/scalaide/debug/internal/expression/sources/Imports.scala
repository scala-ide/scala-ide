/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.sources

import scala.reflect.internal.util.SourceFile

import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.logging.HasLogger

object Imports extends HasLogger with SPCIntegration {

   private val IMPORTS_TIMEOUT_IN_MILLIS = 100 //in millis

  private def reportProblemWithObtainingImports(reason: String): Seq[String] = {
    logger.warn(s"No imports due to: $reason")
    Nil
  }

  private case class ImportExtractor(pc: IScalaPresentationCompiler,
    toLineNumber: Int) {

    class ImportTraverser
      extends pc.Traverser
      with Function1[pc.Tree, Seq[String]] {

      // store trees that create new scope
      private var path: Seq[pc.Tree] = Nil
      // imports per given scope
      private var importsMap: Map[Option[pc.Tree], Seq[pc.Import]] = Map().withDefault(_ => Nil)
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
                importsMap = importsMap + (path.headOption -> (importsMap(path.headOption) :+ importTree))
              case _ =>
            }
            super.traverse(tree)
          }
        }
      }

      override def apply(tree: pc.Tree): Seq[String] = {
        traverse(tree)
        val scopes = None +: path.map(Option.apply)
        scopes.flatMap(importsMap.get).flatten.map(_.toString)
      }
    }

    def importForFile(tree: pc.Tree): Seq[String] =
      new ImportTraverser().apply(tree)
  }

  private def getImports(pc: IScalaPresentationCompiler, sourceFile: SourceFile, line: Int) = {
    pc.askParsedEntered(sourceFile, true).get(IMPORTS_TIMEOUT_IN_MILLIS) match {
      case None => reportProblemWithObtainingImports("timeout")
      case Some(Right(error)) => reportProblemWithObtainingImports(error.getMessage)
      case Some(Left(tree)) =>
        val extractor = new ImportExtractor(pc, line)
        extractor.importForFile(tree.asInstanceOf[extractor.pc.Tree])
    }
  }

  def importsFromCurrentStackFrame: Seq[String] = forCurrentStackFrame(getImports, reportProblemWithObtainingImports)
}


