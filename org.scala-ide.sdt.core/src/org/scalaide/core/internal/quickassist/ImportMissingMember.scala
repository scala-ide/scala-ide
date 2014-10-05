package org.scalaide.core.internal.quickassist

import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.core.search.SearchEngine
import org.eclipse.jdt.core.search.TypeNameMatch
import org.eclipse.jdt.internal.corext.util.TypeNameMatchCollector
import org.eclipse.jdt.ui.JavaUI
import org.scalaide.core.quickassist.BasicCompletionProposal
import org.scalaide.core.quickassist.InvocationContext
import org.scalaide.core.quickassist.QuickAssist
import org.scalaide.util.eclipse.EditorUtils

class ImportMissingMember extends QuickAssist {

  override def compute(ctx: InvocationContext): Seq[BasicCompletionProposal] = {
    val editor = JavaUI.openInEditor(ctx.sourceFile)
    val assists = for {
      location <- ctx.problemLocations
      (ann, pos) <- EditorUtils.getAnnotationsAtOffset(editor, location.getOffset)
    } yield suggestImportFix(ctx.sourceFile, ann.getText)

    assists.flatten
  }

  private def suggestImportFix(compilationUnit: ICompilationUnit, problemMessage: String) = {
    /**
     * Import a type could solve several error message:
     *
     * * "not found : type  Xxxx"
     * * "not found : value Xxxx" in case of java static constant/method like Xxxx.ZZZZ or Xxxx.zzz()
     * * "not found : Xxxx" in case of new Xxxx.eee (IMO (davidB) a better suggestion is to insert (), to have new Xxxx().eeee )
     */
    def suggestImportType(missingType: String) = {
      val typeNames = searchForTypes(compilationUnit.getJavaProject(), missingType)
      typeNames map (name => new ImportCompletionProposal(name.getFullyQualifiedName))
    }

    matchTypeNotFound(problemMessage, suggestImportType)
  }
}
