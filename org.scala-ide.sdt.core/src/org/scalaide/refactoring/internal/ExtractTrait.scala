package org.scalaide.refactoring.internal

import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.common.NewFileChange
import scala.tools.refactoring.common.TextChange
import scala.tools.refactoring.implementations

import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.internal.corext.refactoring.nls.changes.CreateFileChange
import org.eclipse.ltk.core.refactoring.{Change => EclipseChange}
import org.eclipse.ltk.core.refactoring.CompositeChange
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.internal.statistics.Features.ExtractMemberToTrait
import org.scalaide.refactoring.internal.ui.ExtractTraitConfigurationPageGenerator

/**
 * The ExtractTrait refactoring extracts members (vals, vars and defs) of a class
 * or trait to a new trait.
 * The original class/trait will automatically extend the extracted trait and the
 * extracted trait will have a self-type annotation for the original class/trait.
 */
class ExtractTrait extends RefactoringExecutorWithWizard {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = new ExtractTraitScalaIdeRefactoring(selectionStart, selectionEnd, file)

  class ExtractTraitScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile)
    extends ScalaIdeRefactoring(ExtractMemberToTrait, "Extract trait", file, start, end) with ExtractTraitConfigurationPageGenerator {

    val refactoring = withCompiler { c =>
      new implementations.ExtractTrait with GlobalIndexes {
        val global = c
        val index = EmptyIndex
      }
    }

    def refactoringParameters = refactoring.RefactoringParameters(traitName, name => selectedMembers contains name)

    // The preparation result provides a list of members that can be extracted
    private val extractableMembers = preparationResult.right.toOption.map(_.extractableMembers).getOrElse(List())

    // The members selected for extraction in the wizard
    private var selectedMembers: List[refactoring.global.ValOrDefDef] = Nil
    // The name of the extracted trait, will be set in the wizard
    private var traitName = "ExtractedTrait"

    private val configPage = mkConfigPage(
        extractableMembers,
        members => selectedMembers = members,
        name => traitName = name)

    override def getPages = configPage::Nil

    override def createChange(pm: IProgressMonitor): CompositeChange = {
      val (textChanges, newFileChanges) = {
        performRefactoring().foldLeft((List[TextChange](), List[NewFileChange]())) {
          case ((textChanges, newFiles), change: TextChange) =>
            (change :: textChanges, newFiles)
          case ((textChanges, newFilesChanges), change: NewFileChange) =>
            (textChanges, change :: newFilesChanges)
          case (changes, _) => changes
        }
      }

      // Create a new file for the extracted trait
      val fileChange: Option[EclipseChange] = newFileChanges.headOption.map(newFile => {
        val pkg = file.getCompilationUnit.getParent
        val path = pkg.getPath.append(traitName + ".scala")
        new CreateFileChange(path, newFile.text, file.getResource.asInstanceOf[IFile].getCharset)
      })

      val changes = fileChange.toList ::: scalaChangesToEclipseChanges(textChanges).toList
      new CompositeChange(getName, changes.toArray)
    }

  }

}
