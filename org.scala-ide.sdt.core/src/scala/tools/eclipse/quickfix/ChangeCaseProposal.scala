package scala.tools.eclipse.quickfix

import scala.reflect.internal.util.RangePosition
import scala.tools.eclipse.ScalaImages
import scala.tools.eclipse.completion.RelevanceValues
import scala.tools.eclipse.javaelements.ScalaCompilationUnit

import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.Position

/*
 * Find another member with the same spelling but different capitalization.
 * Eg "asdf".subString would offer to change it to .substring instead.
 */

case class ChangeCaseProposal(originalName: String, newName: String, pos: Position) extends BasicCompletionProposal(
  relevance = RelevanceValues.ChangeCaseProposal,
  displayString = s"Change to '${newName}'",
  image = ScalaImages.CORRECTION_RENAME.createImage()) {

  override def apply(document: IDocument): Unit = {
    val offset = pos.offset + pos.length - originalName.length
    document.replace(offset, originalName.length, newName)
  }
}

object ChangeCaseProposal {
  def createProposals(cu: ICompilationUnit, pos: Position, wrongName: String): List[ChangeCaseProposal] = {
    val scu = cu.asInstanceOf[ScalaCompilationUnit]

    def membersAtRange(start: Int, end: Int): List[String] = {
      val memberNames = scu.withSourceFile((srcFile, compiler) => {
        compiler.askOption(() => {
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
        })
      }).flatten
      memberNames.getOrElse(Nil)
    }

    val memberNames = membersAtRange(pos.offset, pos.offset + pos.length - wrongName.length - 1)
    makeProposals(memberNames, wrongName, pos)
  }

  def createProposalsWithCompletion(cu: ICompilationUnit, pos: Position, wrongName: String): List[ChangeCaseProposal] = {
    val scu = cu.asInstanceOf[ScalaCompilationUnit]

    def membersAtPosition(offset: Int): List[String] = {
      val memberNames = scu.withSourceFile((srcFile, compiler) => {
        compiler.askOption(() => {
          val completed = new compiler.Response[List[compiler.Member]]
          compiler.askScopeCompletion(new RangePosition(srcFile, offset, offset, offset), completed)
          completed.get.left.getOrElse(Nil).map(_.sym.nameString).distinct
        })
      }).flatten
      memberNames.getOrElse(Nil)
    }

    val memberNames = membersAtPosition(pos.offset)
    makeProposals(memberNames, wrongName, pos)
  }

  private def makeProposals(memberNames: List[String], wrongName: String, pos: Position): List[ChangeCaseProposal] = {
    val matchingMembers = memberNames.filter(_.equalsIgnoreCase(wrongName))
    for (newName <- matchingMembers) yield ChangeCaseProposal(wrongName, newName, pos)
  }

}