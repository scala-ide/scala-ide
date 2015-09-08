package org.scalaide.core.internal.quickassist.explicit

import scala.reflect.internal.Chars
import scala.tools.nsc.ast.parser.Tokens
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.core.compiler.Token
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist
import org.scalaide.core.internal.statistics.Features.ExplicitReturnType

/** A quick fix that adds an explicit return type to a given val or def */
class ExplicitReturnType extends QuickAssist {
  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] =
    addReturnType(ctx.icu, ctx.selectionStart).toSeq

  private def addReturnType(icu: InteractiveCompilationUnit, offset: Int): Option[BasicCompletionProposal] = {

    icu.withSourceFile { (sourceFile, compiler) =>
      import compiler.ValDef
      import compiler.EmptyTree
      import compiler.TypeTree
      import compiler.DefDef
      import compiler.ValOrDefDef

      /* Find the tokens leading to tree `rhs` and return the position before `=`, or -1 if not found. */
      def findInsertionPoint(vdef: ValOrDefDef): Int = {
        val lexical = new compiler.LexicalStructure(sourceFile)
        val tokens = lexical.tokensBetween(vdef.pos.start, vdef.rhs.pos.start)

        tokens.reverse.find(_.tokenId == Tokens.EQUALS) match {
          case Some(Token(_, start, _)) =>
            var pos = start
            while (sourceFile.content(pos - 1).isWhitespace)
              pos -= 1
            pos
          case _ =>
            -1
        }
      }

      def expandProposal(vd: ValOrDefDef) =
        compiler.asyncExec(compiler.declPrinter.showType(vd.tpt.tpe)).getOption() flatMap { tpe =>
          val insertion = findInsertionPoint(vd)
          // safety check: don't modify anything outside the original tree range
          if (vd.pos.start <= insertion && insertion <= vd.pos.end) {

            // if the last character is an operator char, we need to leave a space
            val colonSpace =
              if (Chars.isOperatorPart(sourceFile.content(insertion - 1))) " : "
              else ": "

            Some(new ExpandText(ExplicitReturnType, 150, s"Add explicit type $tpe", colonSpace + tpe, insertion))
          } else None
        }

      def expandableType(tpt: TypeTree): Boolean = compiler.asyncExec {
        (tpt.original eq null) && !tpt.tpe.isErroneous
      }.getOrElse(false)()

      val enclosing = compiler.enclosingValOrDef(sourceFile, offset)
      if (enclosing != EmptyTree) {
        compiler.askTypeAt(enclosing.pos).getOption() flatMap {
          case vd @ ValDef(_, _, tpt: TypeTree, _) if expandableType(tpt) =>
            expandProposal(vd)
          case dd @ DefDef(_, _, _, _, tpt: TypeTree, _) if expandableType(tpt) =>
            expandProposal(dd)
          case _ => None
        }
      } else None
    }.flatten
  }
}
