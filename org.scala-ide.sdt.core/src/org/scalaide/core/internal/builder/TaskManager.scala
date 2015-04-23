/*
 * Copyright (c) 2015 Contributor. All rights reserved.
 */
package org.scalaide.core.internal.builder

import java.io.InputStream

import scala.reflect.internal.util.Position
import scala.reflect.internal.util.SourceFile

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.internal.core.builder.JavaBuilder
import org.scalaide.core.IScalaProject
import org.scalaide.core.SdtConstants
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.resources.EclipseResource

import scalariform.lexer.ScalaLexer

object TaskManager {

  private case class Comment(msg: String, pos: Position)

  /**
   * Updates all tasks (`TODO`s and `FIXME`s) for given project in given files.
   */
  def updateTasks(project: IScalaProject, files: Set[IFile]): Unit = {
    val taskScanner = new TaskScanner(project)

    for {
      iFile <- files
      scalaFile <- ScalaSourceFile.createFromPath(iFile.getFullPath.toOSString)
      sourceFile = scalaFile.lastSourceMap.sourceFile
      Comment(msg, pos) <- extractComments(sourceFile, iFile.getContents)
      if pos.isDefined
      task <- taskScanner.extractTasks(msg, pos)
      if task.pos.isDefined
    } task.pos.source.file match {
      case EclipseResource(file: IFile) => registerTask(file, task)
      case _ => // ignore
    }
  }

  private def extractComments(sourceFile: SourceFile, contentStream: InputStream): Seq[Comment] = {
    val contents = scala.io.Source.fromInputStream(contentStream).mkString
    for {
      token <- ScalaLexer.rawTokenise(contents, forgiveErrors = true)
      if (token.tokenType.isComment)
    } yield {
      val position = Position.range(sourceFile, token.offset, token.offset, token.lastCharacterOffset)
      Comment(token.text, position)
    }
  }

  private def registerTask(file: IFile, task: TaskScanner.Task) = {
    val marker = file.createMarker(SdtConstants.TaskMarkerId)

    val prioNum = task.priority match {
      case JavaCore.COMPILER_TASK_PRIORITY_HIGH => IMarker.PRIORITY_HIGH
      case JavaCore.COMPILER_TASK_PRIORITY_LOW => IMarker.PRIORITY_LOW
      case _ => IMarker.PRIORITY_NORMAL
    }

    val attributes = Seq(
      IMarker.MESSAGE -> s"${task.tag} ${task.msg}",
      IMarker.PRIORITY -> Integer.valueOf(prioNum),
      IJavaModelMarker.ID -> Integer.valueOf(IProblem.Task),
      IMarker.CHAR_START -> Integer.valueOf(task.pos.start),
      IMarker.CHAR_END -> Integer.valueOf(task.pos.end + 1),
      IMarker.LINE_NUMBER -> Integer.valueOf(task.pos.line),
      IMarker.USER_EDITABLE -> java.lang.Boolean.valueOf(false),
      IMarker.SOURCE_ID -> JavaBuilder.SOURCE_ID)

    attributes.foreach {
      case (key, value) => marker.setAttribute(key, value)
    }
  }
}
