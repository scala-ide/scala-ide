package scala.tools.eclipse
package refactoring

import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.IProgressMonitor
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.eclipse.util.HasLogger
import scala.tools.nsc.util.SourceFile
import scala.tools.refactoring.analysis.GlobalIndexes
import scala.tools.refactoring.common.InteractiveScalaCompiler
import scala.tools.refactoring.MultiStageRefactoring
import org.eclipse.jdt.internal.core.search.PathCollector
import org.eclipse.jdt.internal.core.search.indexing.IndexManager
import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.jdt.core.search.SearchEngine
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.search.SearchPattern
import org.eclipse.jdt.core.search.IJavaSearchConstants
import org.eclipse.jdt.internal.core.search.PatternSearchJob
import org.eclipse.jdt.core.search.SearchParticipant
import org.eclipse.core.runtime.SubProgressMonitor
import org.eclipse.jdt.internal.core.search.BasicSearchEngine
import org.eclipse.jdt.core.search.IJavaSearchScope
import org.eclipse.core.runtime.IPath

/**
 * A trait that can be mixed into refactorings that need an index of the whole 
 * project (e.g. Global Rename, Move Class).
 * 
 * This loads all the files in the project into the presentation compiler, which
 * takes significant time. Once the Scala IDE has its own index, we should be able
 * to make this much more efficient than it currently is.
 */
trait FullProjectIndex extends HasLogger {
  
  val refactoring: MultiStageRefactoring with InteractiveScalaCompiler with GlobalIndexes    
    
  val project: ScalaProject
  
  /**
   * A cleanup handler, will later be set by the refactoring
   * to remove all loaded compilation units from the compiler.
   */
  type CleanupHandler = () => Unit
  
  /**
   * Builds an index from all the source files in the current project. The returned 
   * CleanupHandler needs to be called when the index isn't used anymore, this will
   * then unload all the originally unloaded files from the presentation compiler.
   * 
   * @param hints If present, only files that contain one of these Strings is added
   *              to the index. It uses the JDT SearchEngine to search files.
   */
  def buildFullProjectIndex(pm: IProgressMonitor, hints: List[String]): (refactoring.IndexLookup, CleanupHandler) = {
    
    import refactoring.global
  
    def allProjectSourceFiles: Seq[String] = {
      if(hints.isEmpty) {
        project.allSourceFiles map (_.getFullPath.toString) toSeq
      } else {
        
        val scope = SearchEngine.createJavaSearchScope(Array[IJavaElement](project.javaProject), IJavaSearchScope.SOURCES)
        
        val combinedPattern = hints map { hint =>
          SearchPattern.createPattern(
              hint, IJavaSearchConstants.TYPE, IJavaSearchConstants.ALL_OCCURRENCES,  SearchPattern.R_EXACT_MATCH)
        } reduceLeft SearchPattern.createOrPattern
        
        val pathCollector = new PathCollector
        val indexManager = JavaModelManager.getIndexManager
        indexManager.performConcurrentJob(
          new PatternSearchJob(combinedPattern, BasicSearchEngine.getDefaultSearchParticipant, scope, pathCollector),
          IJavaSearchConstants.FORCE_IMMEDIATE_SEARCH,
          new SubProgressMonitor(pm, hints.size)
        )

        pathCollector.getPaths
      }
    }
    
    def collectAllScalaSources(files: Seq[String]): List[SourceFile] = {
      val allScalaSourceFiles = files flatMap { f =>
        if(pm.isCanceled)
          return Nil
        else 
          ScalaSourceFile.createFromPath(f)
      } toList
      
      allScalaSourceFiles map { ssf =>
        if(pm.isCanceled)
          return Nil
        else
          ssf.withSourceFile { (sourceFile, _) => sourceFile}()
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

      pm.subTask("reloading source files")
      val r = new global.Response[Unit]
      global.askReload(files, r)
      r.get

      files flatMap { f =>
        if(pm.isCanceled) {
          None
        } else {
          val r = new global.Response[global.Tree]
          global.askType(f, forceReload = false /*we just loaded the files*/, r)
          Some(r)
        }
      }        
    }
    
    /**
     * Waits until all the typechecking has finished. Every 200 ms, it is checked
     * whether the user has canceled the process.
     */
    def typeCheckAll(responses: List[global.Response[global.Tree]], pm: IProgressMonitor) = {
      
      def waitForResultOrCancel(r: global.Response[global.Tree]) = {

        var result = None: Option[global.Tree]
        
        do {
          if (pm.isCanceled) r.cancel()
          else r.get(200) match {
            case Some(Left(data)) if r.isComplete /*no provisional results*/ => 
              result = Some(data)
            case _ => // continue waiting
          }
        } while (!r.isComplete && !r.isCancelled && !pm.isCanceled)
          
        result
      }
      
      responses flatMap { 
        case r if !pm.isCanceled => 
          waitForResultOrCancel(r)
        case r =>
          None
      }
    }
          
    pm.beginTask("loading files: ", 3)
            
    // we need to store the already loaded files so that we don't
    // remove them from the presentation compiler later.
    val previouslyLoadedFiles = global.unitOfFile.values map (_.source) toList
    
    val files = collectAllScalaSources(allProjectSourceFiles)
    
    val responses = mapAllFilesToResponses(files, pm)
    
    pm.subTask("typechecking source files")
    
    val trees = typeCheckAll(responses, pm)
    
    // will be called after the refactoring has finished
    val cleanup = { () => 
      (files filterNot previouslyLoadedFiles.contains) foreach {
        global.removeUnitOf
      }
    }
    
    val cus = if(!pm.isCanceled) {
      
      pm.subTask("creating index")
      
      trees flatMap { tree =>
        
        project.withPresentationCompiler { compiler =>
          compiler.askOption { () =>
            refactoring.CompilationUnitIndex(tree)
          }
        }()
      }
    } else Nil
    
    (refactoring.GlobalIndex(cus), cleanup)
  }
}
