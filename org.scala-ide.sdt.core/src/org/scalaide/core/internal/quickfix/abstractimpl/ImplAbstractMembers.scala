package org.scalaide.core.internal.quickfix.abstractimpl

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import scala.tools.refactoring.implementations.AddToClosest
import scala.reflect.internal.util.SourceFile
import scala.collection.immutable

import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._

object ImplAbstractMembers {
  def suggestsFor(ssf: ScalaSourceFile, offset: Int): immutable.Seq[IJavaCompletionProposal] =
    implAbstractMember(ssf, offset)

  def implAbstractMember(ssf: ScalaSourceFile, offset: Int): List[IJavaCompletionProposal] = {
    ssf.withSourceFile { (srcFile, compiler) =>
      import compiler._

      def implAbstractProposals(tree: ImplDef): List[IJavaCompletionProposal] =
        compiler.asyncExec {
          val tp = tree.symbol.tpe
          (tp.members filter { m =>
            // TODO: find the way to get abstract methods simplier
            m.isMethod && m.isIncompleteIn(tree.symbol) && m.isDeferred && !m.isSetter && (m.owner != tree.symbol)
          } map {
            sym =>
              AbstractMemberProposal(compiler)(sym.asMethod, tree)(Option(ssf), AddToClosest(offset))
          }).toList.sortBy(_.defName)
        }.getOrElse(Nil)()

      def createPosition(sf: SourceFile, offset: Int) =
        compiler.rangePos(srcFile, offset, offset, offset)

      def enclosingClassOrModule(src: SourceFile, offset: Int) =
        compiler.locateIn(compiler.parseTree(src), createPosition(src, offset),
          t => (t.isInstanceOf[ClassDef] || t.isInstanceOf[ModuleDef]))

      val enclosingTree = enclosingClassOrModule(srcFile, offset)
      if (enclosingTree != EmptyTree) {
        compiler.withResponse[Tree] { response =>
          compiler.askTypeAt(enclosingTree.pos, response)
        }.get.left.toOption flatMap {
          case implDef: ImplDef =>
            Option(implAbstractProposals(implDef))
          case _ => None
        } getOrElse (Nil)
      } else Nil
    } getOrElse (Nil)
  }
}
