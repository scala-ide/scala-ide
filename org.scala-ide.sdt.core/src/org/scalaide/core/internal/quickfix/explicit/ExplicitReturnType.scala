package org.scalaide.core.internal.quickfix.explicit

import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal
import scala.collection.immutable
import scala.reflect.internal.Chars
import org.scalaide.core.compiler.Token
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import scala.tools.nsc.ast.parser.Tokens

/** A quick fix that adds an explicit return type to a given val or def
 */
object ExplicitReturnType {
  def suggestsFor(ssf: ScalaSourceFile, offset: Int): immutable.Seq[IJavaCompletionProposal] = {
    addReturnType(ssf, offset).toList
  }

  private def addReturnType(ssf: ScalaSourceFile, offset: Int): Option[IJavaCompletionProposal] = {
    ssf.withSourceFile { (sourceFile, compiler) =>
      import compiler.{ Tree, ValDef, EmptyTree, TypeTree, DefDef, ValOrDefDef }

      /** Find the tokens leading to tree `rhs` and return the position before `=`,
       *  or -1 if not found.
       */
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

      def expandProposal(vd: ValOrDefDef): Option[IJavaCompletionProposal] =
        compiler.askOption { () => vd.tpt.toString } flatMap { tpe =>
          val insertion = findInsertionPoint(vd)
          // safety check: don't modify anything outside the original tree range
          if (vd.pos.start <= insertion && insertion <= vd.pos.end) {

            // if the last character is an operator char, we need to leave a space
            val colonSpace =
              if (Chars.isOperatorPart(sourceFile.content(insertion - 1))) " : "
              else ": "

            Some(new ExpandText(150, s"Add explicit type $tpe", colonSpace + tpe, insertion))
          } else None
        }

      def expandableType(tpt: TypeTree) = compiler.askOption { () =>
        (tpt.original eq null) && !tpt.tpe.isErroneous
      }.getOrElse(false)

      val enclosing = compiler.enclosingValOrDef(sourceFile, offset)
      if (enclosing != EmptyTree) {
        compiler.withResponse[Tree] { response =>
          compiler.askTypeAt(enclosing.pos, response)
        }.get.left.toOption flatMap {
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
