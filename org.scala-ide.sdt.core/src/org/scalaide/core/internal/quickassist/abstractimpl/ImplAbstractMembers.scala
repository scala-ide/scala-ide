package org.scalaide.core.internal.quickassist.abstractimpl

import scala.reflect.internal.util.SourceFile
import scala.tools.refactoring.implementations.AddToClosest

import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist

class ImplAbstractMembers extends QuickAssist {
  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] =
    implAbstractMember(ctx.icu, ctx.selectionStart)

  def implAbstractMember(icu: InteractiveCompilationUnit, offset: Int): Seq[BasicCompletionProposal] = {
    icu.withSourceFile { (srcFile, compiler) =>
      import compiler._

      def implAbstractProposals(tree: ImplDef) =
        compiler.asyncExec {
          val tp = tree.symbol.tpe
          (tp.members filter { m =>
            // TODO: find the way to get abstract methods simpler
            m.isMethod && m.isIncompleteIn(tree.symbol) && m.isDeferred && !m.isSetter && (m.owner != tree.symbol)
          } map {
            sym =>
              AbstractMemberProposal(compiler)(sym.asMethod, tree)(Option(icu), AddToClosest(offset))
          }).toList.sortBy(_.defName)
        }.getOrElse(Nil)()

      def createPosition(sf: SourceFile, offset: Int) =
        compiler.rangePos(srcFile, offset, offset, offset)

      def enclosingClassOrModule(src: SourceFile, offset: Int) =
        compiler.locateIn(compiler.parseTree(src), createPosition(src, offset),
          t => (t.isInstanceOf[ClassDef] || t.isInstanceOf[ModuleDef]))

      val enclosingTree = enclosingClassOrModule(srcFile, offset)
      if (enclosingTree != EmptyTree) {
          compiler.askTypeAt(enclosingTree.pos).getOption() flatMap {
          case implDef: ImplDef =>
            Option(implAbstractProposals(implDef))
          case _ => None
        } getOrElse (Nil)
      } else Nil
    } getOrElse (Nil)
  }
}
