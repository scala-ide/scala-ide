package org.scalaide.editor

import scala.tools.eclipse.ScalaPresentationCompiler
import scala.reflect.internal.util.SourceFile

/** A few convenience methods on top of the presentation compiler
 *
 *  This should migrate to sdt.core/interactive compiler as needed.
 */
abstract class PresentationCompilerExtensions {
  val compiler: ScalaPresentationCompiler

  import compiler._

  /** Locate smallest tree that encloses position.
   *
   *  Returns `EmptyTree` if the position could not be found.
   */
  def locateIn(tree: Tree, pos: Position, p: Tree => Boolean = t => true): Tree =
    new FilteringLocator(pos, p) locateIn tree

  class FilteringLocator(pos: Position, p: Tree => Boolean) extends Locator(pos) {
    override def isEligible(t: Tree) = super.isEligible(t) && p(t)
  }

  def getEnclosingMethd(src: SourceFile, offset: Int) = {
    locateIn(parseTree(src), rangePos(src, offset, offset, offset), t => t.isInstanceOf[DefDef])
  }
}