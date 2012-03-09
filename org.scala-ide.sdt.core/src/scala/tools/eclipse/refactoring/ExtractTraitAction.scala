package scala.tools.eclipse
package refactoring

import scala.tools.eclipse.refactoring.ui.ExtractTraitConfigurationPageGenerator
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.common.NewFileChange
import scala.tools.refactoring.common.TextChange
import scala.tools.refactoring.implementations.ExtractTrait

import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.internal.corext.refactoring.nls.changes.CreateFileChange
import org.eclipse.ltk.core.refactoring.{Change => EclipseChange}
import org.eclipse.ltk.core.refactoring.CompositeChange

import javaelements.ScalaSourceFile

/**
 * The ExtractTrait refactoring extracts members (vals, vars and defs) of a class
 * or trait to a new trait. 
 * The original class/trait will automatically extend the extracted trait and the
 * extracted trait will have a self-type annotation for the original class/trait.
 */
class ExtractTraitAction extends RefactoringAction {

  def createRefactoring(selectionStart: Int, selectionEnd: Int, file: ScalaSourceFile) = new ExtractTraitScalaIdeRefactoring(selectionStart, selectionEnd, file)
  
  class ExtractTraitScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile)
    extends ScalaIdeRefactoring("Extract trait", file, start, end) with ExtractTraitConfigurationPageGenerator {
    
    val refactoring = withCompiler { c =>
      new ExtractTrait with GlobalIndexes {
        val global = c
        var index = GlobalIndex(Nil)
      }
    }
    
    def refactoringParameters = refactoring.RefactoringParameters(traitName, name => selectedMembers contains name)
    
    // The preparation result provides a list of members that can be extracted
    val extractableMembers = preparationResult.right.get.extractableMembers
    
    // The members selected for extraction in the wizard
    var selectedMembers: List[refactoring.global.ValOrDefDef] = Nil
    // The name of the extracted trait, will be set in the wizard
    var traitName = "ExtractedTrait"
    
    val configPage = mkConfigPage(
        extractableMembers, 
        members => selectedMembers = members, 
        name => traitName = name)
    
    override def getPages = configPage::Nil

    override def createChange(pm: IProgressMonitor): CompositeChange = {
      val (textChanges, newFileChanges) = {
        performRefactoring().foldRight((List[TextChange](), List[NewFileChange]())) {
          case (change: TextChange, (textChanges, newFiles)) =>
            (change :: textChanges, newFiles)
          case (change: NewFileChange, (textChanges, newFilesChanges)) =>
            (textChanges, change :: newFilesChanges)
        }
      }
      
      // Create a new file for the extracted trait
      val fileChange: Option[EclipseChange] = newFileChanges.headOption.map(newFile => {
        val pkg = file.getCompilationUnit.getParent
        val path = pkg.getPath.append(traitName + ".scala")
        new CreateFileChange(path, newFile.text, file.getResource.asInstanceOf[IFile].getCharset)
      })
      
      val changes = fileChange.toList ::: scalaChangesToEclipseChanges(textChanges).toList
      val compositeChange = new CompositeChange(getName)
      changes.foreach(compositeChange.add)
      
      compositeChange
    }
    
  }
  
}