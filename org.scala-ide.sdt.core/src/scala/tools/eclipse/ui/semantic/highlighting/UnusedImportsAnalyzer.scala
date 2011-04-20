package scala.tools.eclipse
package ui.semantic.highlighting

import org.eclipse.core.resources.IMarker
import scala.tools.refactoring.implementations.UnusedImportsFinder
import org.eclipse.core.resources.IFile
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.refactoring.common.TreeTraverser
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.util.FileUtils
import scala.tools.nsc.io.AbstractFile

object UnusedImportsAnalyzer {
  val warningMarkerId = ScalaPlugin.plugin.problemMarkerId

  def markUnusedImports(file: IFile) {
    for (
      afile <- FileUtils.toAbstractFile(Option(file));
      project <- Option(ScalaPlugin.plugin.getScalaProject(file.getProject))//ScalaSourceFile.createFromPath(file.getFullPath.toString) 
    ) {
      val unuseds : List[(String, Int)] = project.withPresentationCompiler{ compiler =>
        compiler.ask { () =>
          new UnusedImportsFinder {
            val global = compiler
            val unit = global.unitOfFile(afile)
            def compilationUnitOfFile(f: AbstractFile) = global.unitOfFile.get(f)
            val unuseds : List[(String, Int)] = findUnusedImports(unit)
          }.unuseds
        }
      }(Nil)
      for (
        (filepath, line) <- unuseds;
        ifile <- FileUtils.toIFile(filepath)
      ) {
        //TODO check ifile == file
        putMarker(ifile, line)
      }
    }
  }

  //TODO use utils.Annotations
  private def putMarker(file : IFile, line: Int) {
    try {
      val mrk = file.createMarker(warningMarkerId)
      mrk.setAttribute(IMarker.SEVERITY, IMarker.SEVERITY_WARNING)
      mrk.setAttribute(IMarker.MESSAGE, "Unused import");
      mrk.setAttribute(IMarker.LINE_NUMBER, line)
    } catch {
      case e: Exception => e.printStackTrace();
    }
  }
}