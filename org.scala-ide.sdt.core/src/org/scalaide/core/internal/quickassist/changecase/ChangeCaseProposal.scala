package org.scalaide.core.internal.quickassist
package changecase

import scala.reflect.internal.util.RangePosition

import org.eclipse.jface.text.IDocument
import org.scalaide.core.compiler.InteractiveCompilationUnit
import org.scalaide.core.internal.quickassist.RelevanceValues
import org.scalaide.core.internal.statistics.Features.FixSpellingMistake
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.ui.ScalaImages

/*
 * Find another member with the same spelling but different capitalization.
 * Eg "asdf".subString would offer to change it to .substring instead.
 */
case class ChangeCaseProposal(originalName: String, newName: String, offset: Int, length: Int) extends BasicCompletionProposal(
  feature = FixSpellingMistake,
  relevance = RelevanceValues.ChangeCaseProposal,
  displayString = s"Change to '${newName}'",
  image = ScalaImages.CORRECTION_RENAME.createImage()) {

  override def applyProposal(document: IDocument): Unit = {
    val o = offset + length - originalName.length
    document.replace(o, originalName.length, newName)
  }
}

object ChangeCaseProposal {
  import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._

  def createProposals(icu: InteractiveCompilationUnit, offset: Int, length: Int, wrongName: String): List[ChangeCaseProposal] = {
    def membersAtRange(start: Int, end: Int): List[String] = {
      val memberNames = icu.withSourceFile { (srcFile, compiler) =>
        compiler asyncExec {
          val length = end - start

          /*
           * I wish we could use askTypeCompletion (similar to createProposalsWithCompletion),
           * but because of the error the compiler won't give it to us.
           * Because of this, a limitation is that we can't fix the capitalization when it must
           * be found via implicit conversion.
           */

          val context = compiler.doLocateContext(new RangePosition(srcFile, start, start, start + length))
          val tree = compiler.locateTree(new RangePosition(srcFile, start, start, start + length))
          val typer = compiler.analyzer.newTyper(context)
          val typedTree = typer.typed(tree)
          val tpe = typedTree.tpe.resultType.underlying
          if (tpe.isError) Nil else tpe.members.map(_.nameString).toList.distinct
        } getOption()
      }

      memberNames.flatten.getOrElse(Nil)
    }

    val memberNames = membersAtRange(offset, offset + length - wrongName.length - 1)
    makeProposals(memberNames, wrongName, offset, length)
  }

  def createProposalsWithCompletion(icu: InteractiveCompilationUnit, offset: Int, length: Int, wrongName: String): List[ChangeCaseProposal] = {
    def membersAtPosition(offset: Int): List[String] = {
      val memberNames = icu.withSourceFile { (srcFile, compiler) =>
        compiler.asyncExec {
          val completed = compiler.askScopeCompletion(new RangePosition(srcFile, offset, offset, offset))
          completed.getOrElse(Nil)().map(_.sym.nameString).distinct
        } getOption()
      }
      memberNames.flatten.getOrElse(Nil)
    }

    val memberNames = membersAtPosition(offset)
    makeProposals(memberNames, wrongName, offset, length)
  }

  private def makeProposals(memberNames: List[String], wrongName: String, offset: Int, length: Int): List[ChangeCaseProposal] = {
    val matchingMembers = memberNames.filter(_.equalsIgnoreCase(wrongName))
    for (newName <- matchingMembers) yield ChangeCaseProposal(wrongName, newName, offset, length)
  }

}
