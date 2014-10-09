package org.scalaide.core.internal.quickassist

import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist

class ImportMissingMember extends QuickAssist {

  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] = {
    /*
     * Import a type could solve several error message:
     *
     * - "not found : type  Xxxx"
     * - "not found : value Xxxx" (in case of Java static constant/method like Xxxx.ZZZZ)
     * - "not found : Xxxx" (in case of new Xxxx.eee)
     */
    def suggestImportType(missingType: String) = {
      val typeNames = searchForTypes(ctx.icu.scalaProject.javaProject, missingType)
      typeNames map (name => new ImportCompletionProposal(name.getFullyQualifiedName))
    }

    ctx.problemLocations flatMap { location =>
      matchTypeNotFound(location.annotation.getText, suggestImportType)
    }
  }
}
