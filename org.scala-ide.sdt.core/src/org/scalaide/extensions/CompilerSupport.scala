package org.scalaide.extensions

import scala.reflect.internal.util.SourceFile
import scala.tools.refactoring.Refactoring
import scala.tools.refactoring.common.InteractiveScalaCompiler

import org.scalaide.core.text.Change
import org.scalaide.core.text.TextChange

/**
 * Can be mixed into a [[ScalaIdeExtension]] that operates on the data
 * structures of the compiler.
 */
trait CompilerSupport
    extends ScalaIdeExtension
    with Refactoring
    with InteractiveScalaCompiler {

  import global._

  /**
   * The selection of the active editor at the time when a save event occurs.
   *
   * '''ATTENTION''':
   * Do not implement this value by any means! It will be automatically
   * implemented by the IDE.
   */
  val selection: Selection

  /**
   * Gives access to the underlying document.
   *
   * '''ATTENTION''':
   * Do not implement this value by any means! It will be automatically
   * implemented by the IDE.
   */
  val sourceFile: SourceFile

  /**
   * Applies a transformation to the tree of the saved document.
   */
  final def transformFile(trans: Transformation[Tree, Tree]): Seq[Change] =
    refactor(trans(abstractFileToTree(sourceFile.file)).toList) map {
      case scala.tools.refactoring.common.TextChange(_, from, to, text) ⇒
        TextChange(from, to, text)
    }
}
