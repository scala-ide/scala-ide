package org.scalaide.core.internal.quickassist
package createmethod

import scala.tools.refactoring.implementations.AddToClass
import scala.tools.refactoring.implementations.AddToClosest
import scala.tools.refactoring.implementations.AddToObject

import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist

class CreateMethod extends QuickAssist {

  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] = {
    ctx.problemLocations flatMap { location =>
      val possibleMatch = location.annotation.getText match {
        case ValueNotAMemberOfObject(member, theType) =>
          List(CreateMethodProposal(Some(theType), member, AddToObject, ctx.icu, location.offset, location.length))
        case ValueNotAMember(member, theType) =>
          List(CreateMethodProposal(Some(theType), member, AddToClass, ctx.icu, location.offset, location.length))
        case ValueNotFoundError(member) =>
          List(CreateMethodProposal(None, member, AddToClosest(location.offset), ctx.icu, location.offset, location.length))
        case _ =>
          Nil
      }
      possibleMatch.filter(_.isApplicable)
    }
  }
}
