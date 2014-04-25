/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.sources

import scala.reflect.internal.util.SourceFile

import org.eclipse.core.resources.IFile
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.debug.internal.ScalaDebugger
import org.scalaide.debug.internal.model.ScalaStackFrame
import org.scalaide.logging.HasLogger

object Imports extends HasLogger {

  /** Extract imports from scala file that apply for given line */
  private case class ImportExtractor(
    pc: IScalaPresentationCompiler,
    toLineNumber: Int) {

    private class ImportTraverser
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

    def importForFile(sourceFile: SourceFile): Seq[String] =
      pc.askParsedEntered(sourceFile, true).get(100) match {
        case None => noImports("timeout")
        case Some(Right(error)) => noImports(error.getMessage)
        case Some(Left(tree)) => new ImportTraverser().apply(tree)
      }
  }

  private def noImports(reason: String): Seq[String] = {
    logger.warn(s"No imports due to: $reason")
    Nil
  }

  private def fromFile(file: IFile, line: Int): Seq[String] = {
    val path = file.getFullPath.toOSString
    val scalaProject = IScalaPlugin().getScalaProject(file.getProject)
    ScalaSourceFile.createFromPath(path).map { scalaFile =>
      scalaProject.presentationCompiler.apply { pc =>
        new ImportExtractor(pc, line).importForFile(scalaFile.lastSourceMap().sourceFile)
      }.getOrElse(noImports("presenation compiler is broken?"))
    }.getOrElse(noImports("No such file"))
  }

  def forCurrentStackFrame: Seq[String] = {
    val ssf = ScalaStackFrame(ScalaDebugger.currentThread, ScalaDebugger.currentFrame().get)
    ScalaDebugger.currentThread.getDebugTarget.getLaunch
      .getSourceLocator.getSourceElement(ssf) match {
        case file: IFile =>
          fromFile(file, ssf.getLineNumber())
        case _ => noImports("Source file not found for: " + ssf.getSourcePath())
      }
  }
}


