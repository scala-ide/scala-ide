/*
 * Copyright (c) 2014 - 2015 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.sources

import scala.reflect.internal.util.SourceFile

import org.eclipse.core.resources.IFile
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.compiler.IScalaPresentationCompiler
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.debug.internal.ScalaDebugger
import org.scalaide.debug.internal.model.ScalaStackFrame

/** provides logic for scala presentation compiler integration */
trait SPCIntegration {
  type PCFunction[T] = (IScalaPresentationCompiler, SourceFile, Int) => T

  /**
   * Obtain scala presentation compiler for current stack frame
   */
  protected def forCurrentStackFrame[T](fun: PCFunction[T], onError: String => T): T = try {
    val ssf = ScalaStackFrame(ScalaDebugger.currentThread, ScalaDebugger.currentFrame().get)
    ScalaDebugger.currentThread.getDebugTarget.getLaunch
      .getSourceLocator.getSourceElement(ssf) match {
      case file: IFile =>
        val path = file.getFullPath.toOSString
        val scalaProject = IScalaPlugin().getScalaProject(file.getProject)

        ScalaSourceFile.createFromPath(path).fold(onError("No such file"))(scalaFile =>
          scalaProject.presentationCompiler.apply { pc =>
            fun(pc, scalaFile.lastSourceMap().sourceFile, ssf.getLineNumber())
          }.getOrElse(onError("presenation compiler is broken?")))

      case _ => onError("Source file not found for: " + ssf.getSourcePath())
    }
  } catch {
    case error: Throwable => onError(error.getMessage)
  }

}
