package org.scalaide.core.internal.builder.zinc

import java.io.File

import xsbti.compile.MultipleOutput
import xsbti.compile.OutputGroup

/**
 * This class is serialized by Zinc. Don't forget to get rid off all closure dependences
 * in case of re-implementing it.
 */
class EclipseMultipleOutput(val srcOuts: Seq[(File, File)]) extends MultipleOutput {
  override def getOutputGroups = srcOuts.map {
    case (src, out) => new OutputGroup {
      override def getSourceDirectory = src
      override def getOutputDirectory = out
    }
  }.toArray
}
