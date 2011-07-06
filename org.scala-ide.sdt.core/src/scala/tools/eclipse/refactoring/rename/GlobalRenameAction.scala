/*
 * Copyright 2005-2010 LAMP/EPFL
 */

package scala.tools.eclipse
package refactoring
package rename

import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.ltk.core.refactoring.resource.RenameResourceChange
import org.eclipse.ltk.core.refactoring.RefactoringStatus
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.refactoring.ui._
import scala.tools.nsc.util.SourceFile
import scala.tools.refactoring.analysis.{GlobalIndexes, Indexes, NameValidation}
import scala.tools.refactoring.common.{ConsoleTracing, InteractiveScalaCompiler, Selections}
import scala.tools.refactoring.implementations.Rename
import scala.tools.refactoring.Refactoring

/**
 * Renames using a wizard and a change preview. This action is used
 * for all global rename refactorings and also from the RenameParticipant.
 * 
 * When a class is renamed that has the same name as the source file,
 * the file is renamed too.
 */
class GlobalRenameAction extends RefactoringAction {
  
  def createRefactoring(start: Int, end: Int, file: ScalaSourceFile) = new RenameScalaIdeRefactoring(start, end, file)

  /**
   * The actual refactoring instance that is used by the RefactoringAction.
   */
  class RenameScalaIdeRefactoring(start: Int, end: Int, file: ScalaSourceFile) extends ScalaIdeRefactoring("Rename", file, start, end) {
      
    var name = ""
      
    def refactoringParameters = name
    
    val refactoring = withCompiler { compiler =>  
      new Rename with GlobalIndexes with NameValidation { 
        val global = compiler
        
        /* The initial index is empty, it will be filled during the initialization
         * where we can show a progress bar and let the uer cancel the operation.*/
        var index = GlobalIndex(Nil)
      }
    }
    
    /**
     * A cleanup handler, will later be set by the refactoring
     * to remove all loaded compilation units from the compiler.
     */
    var cleanup = () => ()
    
    /* (non-Javadoc)
     * @see scala.tools.eclipse.refactoring.ScalaIdeRefactoring#checkInitialConditions(org.eclipse.core.runtime.IProgressMonitor)
     */
    override def checkInitialConditions(pm: IProgressMonitor): RefactoringStatus = {

      val allProjectSourceFiles = file.project.allSourceFiles.toList
      
      def initializeDefaultNameFromSelectedSymbol() = {
        preparationResult.right.foreach { preparationResult =>
          name = preparationResult.selectedTree.symbol.nameString
        }
      }
      
      def collectAllScalaSources(files: List[IFile]) = {
        val allScalaSourceFiles = files flatMap { f =>
          ScalaSourceFile.createFromPath(f.getFullPath.toString)
        }
        
        allScalaSourceFiles map { ssf => 
          ssf.withSourceFile { (sourceFile, _) => sourceFile
          }()
        }
      }
      
      /**
       * First loads all the source files into the compiler and then starts
       * typeckecking them. The method won't block until typechecking is done
       * but return all the Response objects instead.
       * 
       * If the process gets canceled, no more new typechecks will be started.
       */
      def mapAllFilesToResponses(files: List[SourceFile], pm: IProgressMonitor) = {

        pm.subTask("Loading source files.")

        val r = new refactoring.global.Response[Unit]
        refactoring.global.askReload(files, r)
        r.get
        
        files flatMap { f =>
          if(pm.isCanceled) {
            None
          } else {
            val r = new refactoring.global.Response[refactoring.global.Tree]
            refactoring.global.askType(f, forceReload = false, r)
            Some(r)
          }
        }        
      }
      
      /**
       * Waits until all the typechecking has finished. Every 200 ms, it is checked
       * whether the user has canceled the process.
       */
      def typeCheckAll(responses: List[refactoring.global.Response[refactoring.global.Tree]], pm: IProgressMonitor) = {
        
        import refactoring.global._
        
        def waitForResultOrCancel(r: Response[Tree]) = {

          var result = None: Option[Tree]
          
          do {
            if (pm.isCanceled) r.cancel()
            else r.get(200) match {
              case Some(Left(data)) if r.isComplete /*no provisional results*/ => 
                result = Some(data)
              case _ => // continue waiting
            }
          } while (!r.isComplete && !r.isCancelled)
            
          result
        }
        
        responses flatMap { 
          case r if !pm.isCanceled => 
            waitForResultOrCancel(r)
          case r =>
            None
        }
      }
      
      initializeDefaultNameFromSelectedSymbol()
      
      pm.beginTask("loading files for renaming", 3)
              
      // we need to store the already loaded files so that don't
      // remove them from the presentation compiler later.
      val previouslyLoadedFiles = refactoring.global.unitOfFile.values map (_.source) toList
      
      val files = collectAllScalaSources(allProjectSourceFiles)
      
      val responses = mapAllFilesToResponses(files, pm)
      
      pm.subTask("typechecking source files")
      
      val trees = typeCheckAll(responses, pm)
      
      if(!pm.isCanceled) {
        
        pm.subTask("creating index for renaming")
        
        val cus = trees map refactoring.CompilationUnitIndex.apply
        
        refactoring.index = refactoring.GlobalIndex(cus)
      }
      
      // will be called after the refactoring has finished
      cleanup = { () => 
        (files filterNot previouslyLoadedFiles.contains) foreach {
          refactoring.global.removeUnitOf
        }
      }

      val status = super.checkInitialConditions(pm)
      
      if(pm.isCanceled) {
        status.addWarning("Indexing was cancelled, types will not be renamed.")
      }

      status
    }
      
    override def checkFinalConditions(pm: IProgressMonitor): RefactoringStatus = {
      val status = super.checkFinalConditions(pm)
      
      refactoring.doesNameCollide(name, selection.selectedSymbolTree map (_.symbol) getOrElse refactoring.global.NoSymbol) match {
        case Nil => ()
        case collisions => 
          val names = collisions map (s => s.fullName) mkString ", "
          status.addWarning("The name \""+ name +"\" is already in use: "+ names)
      }
      
      status
    }
      
    override def getPages = new NewNameWizardPage((s => name = s), refactoring.isValidIdentifier, name, "refactoring_rename") :: Nil
      
    override def createChange(pm: IProgressMonitor) = {
      val compositeChange = super.createChange(pm)
      
      preparationResult.right.get.selectedTree match {
        case impl: refactoring.global.ImplDef if impl.name.toString + ".scala" == file.file.name =>
          file.getCorrespondingResource match {
            case ifile: IFile =>
              compositeChange.add(new RenameResourceChange(ifile.getFullPath, name + ".scala"))
            case _ =>
          }
        case _ =>
      }
      
      compositeChange
    }
  }
}
