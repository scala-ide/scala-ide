package org.scalaide.refactoring.internal.extract

import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import scala.tools.refactoring.common.Occurrences
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.common.InteractiveScalaCompiler
import org.scalaide.util.internal.eclipse.EditorUtils

case class LocalNameOccurrences(name: String) {
  private val os =
    EditorUtils.withCurrentScalaSourceFile { file =>
      file.withSourceFile { (sourceFile, compiler) =>
        new Occurrences with GlobalIndexes with InteractiveScalaCompiler {
          val global = compiler
          val (root, index) = {
            val tree = askLoadedAndTypedTreeForFile(sourceFile).left.get
            (tree, global.ask(() => GlobalIndex(tree)))
          }
        }
      }.get
    }.get

  def termNameOccurrences(name: String): List[(Int, Int)] =
    os.global.ask { () =>
      os.termNameOccurrences(os.root, name)
    }

  def parameterOccurrences(defName: String): List[List[(Int, Int)]] =
    os.global.ask { () =>
      os.defDefParameterOccurrences(os.root, defName)
    }

  def performInlineRenaming(): Unit = {
    val nameOccurrences = termNameOccurrences(name)
    val paramOccurrences = parameterOccurrences(name)

    EditorUtils.enterMultiLinkedModeUi(nameOccurrences :: paramOccurrences, selectFirst = true)
  }
}