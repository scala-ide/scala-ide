package org.scalaide.core.internal.compiler

import org.scalaide.core.compiler.IPositionInformation
import scala.reflect.internal.util.SourceFile
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.io.AbstractFile
import org.scalaide.core.compiler.ISourceMap

/** An implementation of position information that is based on a Scala SourceFile implementation
 */
class PlainScalaPosition(sourceFile: SourceFile) extends IPositionInformation {
  def apply(pos: Int): Int = pos

  def offsetToLine(offset: Int): Int = sourceFile.offsetToLine(offset)

  def lineToOffset(line: Int): Int = sourceFile.lineToOffset(line)
}

/** An implementation of `ISourceMap` that is the identity transformation. */
class PlainScalaInfo(file: AbstractFile, override val originalSource: Array[Char]) extends ISourceMap {
  override lazy val sourceFile = new BatchSourceFile(file, scalaSource)
  override val scalaPos = IPositionInformation.plainScala(sourceFile)
  override val originalPos = IPositionInformation.plainScala(sourceFile)

  override def scalaLine(line: Int): Int = line
  override def originalLine(line: Int): Int = line
}
