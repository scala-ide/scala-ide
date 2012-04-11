package scala.tools.eclipse.semantichighlighting.classifier

import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.collection.mutable.ListBuffer

trait TypeTreeTraverser {
  val global: tools.nsc.Global

  import global._

  implicit def tree2treeWrapper(tree: Tree): TreeWrapper = new TreeWrapper(tree)

  /**
   * Monadic wrapper for a Tree. The wrapper is actually very similar
   * to {{{TreeOps}}}, with the difference that is uses the below defined
   * {{{TypeTreeTraverser}}} in place of the standard {{{Traverser}}} to
   * traverse the Tree.
   */
  class TreeWrapper(tree: Tree) {
    private val adaptee = new TreeOps(tree)

    def isType: Boolean = adaptee.isType

    def foreach(f: Tree => Unit) {
      new ForeachTypeTreeTraverser(f).traverse(tree)
    }
    // TODO: Need to replace {{{filter}} with {{{withFilter}}}
    def filter(p: Tree => Boolean): List[Tree] = {
      val ft = new FilterTypeTreeTraverser(p)
      ft.traverse(tree)
      ft.hits.toList
    }
  }

  //Note [mirco]: The implementation was copied from {{{Trees.ForeachTreeTraverser}}}. Can this be avoided?
  private class ForeachTypeTreeTraverser(f: Tree => Unit) extends TypeTreeTraverser {
    override def traverse(t: Tree) {
      f(t)
      super.traverse(t)
    }
  }

  //Note [mirco]: The implementation was copied from {{{Trees.ForeachTreeTraverser}}}. Can this be avoided?
  private class FilterTypeTreeTraverser(p: Tree => Boolean) extends TypeTreeTraverser {
    val hits = new ListBuffer[Tree]
    override def traverse(t: Tree) {
      if (p(t)) hits += t
      super.traverse(t)
    }
  }

  /**
   * Traverse {{{TypeTree}} AST nodes if an {{{original}}} (underlying) Tree exists.
   * This let us decompose traverse type trees such as {{{ExistentialTypeTree}}} which
   * would otherwise be ignored by the standard {{{scala.tools.nsc.Trees.Traverser}}}
   * implementation
   */
  private trait TypeTreeTraverser extends Traverser {
    override def traverse(tree: Tree): Unit = tree match {
      case tpeTree: TypeTree =>
        val original = tpeTree.original
        if (original == null || original == tpeTree) super.traverse(tpeTree)
        else traverse(original)

      case _ => super.traverse(tree)
    }
  }
}