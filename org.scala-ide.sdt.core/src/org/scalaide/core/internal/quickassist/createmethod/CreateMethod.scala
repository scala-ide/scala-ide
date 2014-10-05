package org.scalaide.core.internal.quickassist
package createmethod

import scala.tools.refactoring.implementations.AddToClass
import scala.tools.refactoring.implementations.AddToClosest
import scala.tools.refactoring.implementations.AddToObject

import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.text.Position
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist
import org.scalaide.util.eclipse.EditorUtils

class CreateMethod extends QuickAssist {

  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] = {
    val editor = JavaUI.openInEditor(ctx.sourceFile)
    val assists = for {
      location <- ctx.problemLocations
      (ann, pos) <- EditorUtils.getAnnotationsAtOffset(editor, location.getOffset)
    } yield suggestCreateMethodFix(ctx.sourceFile, ann.getText, pos)

    assists.flatten
  }

  private def suggestCreateMethodFix(compilationUnit: ICompilationUnit, problemMessage: String, pos: Position) = {
    val possibleMatch = problemMessage match {
      case ValueNotAMemberOfObject(member, theType) => List(CreateMethodProposal(Some(theType), member, AddToObject, compilationUnit, pos))
      case ValueNotAMember(member, theType)         => List(CreateMethodProposal(Some(theType), member, AddToClass, compilationUnit, pos))
      case ValueNotFoundError(member)               => List(CreateMethodProposal(None, member, AddToClosest(pos.offset), compilationUnit, pos))
      case _                                        => Nil
    }
    possibleMatch.filter(_.isApplicable)
  }
}
