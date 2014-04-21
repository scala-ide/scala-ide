package org.scalaide.refactoring.internal.extract

import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.common.InteractiveScalaCompiler
import scala.tools.refactoring.common.Occurrences

import org.scalaide.core.internal.jdt.model.ScalaSourceFile

class OccurrenceFinder(file: ScalaSourceFile) {
  private val os = file.withSourceFile { (sourceFile, compiler) =>
    new Occurrences with GlobalIndexes with InteractiveScalaCompiler {
      val global = compiler
      val (root, index) = {
        val tree = askLoadedAndTypedTreeForFile(sourceFile).left.get
        (tree, global.ask(() => GlobalIndex(tree)))
      }
    }
  }.get

  def termNameOccurrences(name: String): List[(Int, Int)] =
    os.global.ask { () =>
      os.termNameOccurrences(os.root, name)
    }

  def parameterOccurrences(defName: String): List[List[(Int, Int)]] =
    os.global.ask { () =>
      os.defDefParameterOccurrences(os.root, defName)
    }
}