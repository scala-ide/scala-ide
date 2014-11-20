/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.debug.internal.expression.sources

import org.scalaide.core.compiler.IScalaPresentationCompiler
import scala.reflect.internal.util.SourceFile
import org.scalaide.debug.internal.model.ScalaStackFrame
import org.scalaide.debug.internal.ScalaDebugger
import org.eclipse.core.resources.IFile
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.internal.jdt.model.ScalaSourceFile

trait PCIntegration {
  type PCFunction[T] = (IScalaPresentationCompiler, SourceFile, Int) => T

  /**
   * Obtain scala presentation compiler for current stack frame
   */
  protected def forCurrentStackFrame[T](fun: PCFunction[T], onError: String => T): T = {
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
  }

}
