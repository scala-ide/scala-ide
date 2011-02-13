package scala.tools.eclipse
package ui.semantic.highlighting

import org.eclipse.core.resources.IMarker
import scala.tools.refactoring.implementations._
import org.eclipse.core.resources.IFile
import scala.tools.eclipse.javaelements.ScalaSourceFile
import scala.tools.refactoring.common.TreeTraverser
import scala.tools.refactoring.transformation.TreeTransformations
import scala.tools.refactoring.common.CompilerAccess
import scala.tools.refactoring.common.PimpedTrees
import scala.tools.eclipse.ScalaPlugin._
import scala.tools.refactoring.sourcegen.SourceGenerator
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.jdt.core.WorkingCopyOwner

object UnusedImportsAnalyzer {
  val warningMarkerId = plugin.problemMarkerId

  def markUnusedImports(file: IFile) {
    for (
      scalaSourceFile <- ScalaSourceFile.createFromPath(file.getFullPath.toString) 
    ) {
      scalaSourceFile.withSourceFile { (sourceFile, compiler) =>
        compiler.ask { () =>
          new UnusedImportsFinder {
            val global = compiler
            import global._
            
            compilationUnitOfFile(scalaSourceFile.file).map(unit => {
              val warningsSetter = new Traverser {
                override def traverse(tree: Tree): Unit = tree match {
                  case Import(expr, selectors) => {
                    val needed = selectors.filter(s => neededImportSelector(unit, expr, s))
                    if (needed.size == 0)
                      putMarker(file, tree.pos.line)
                  }
                  case _ => super.traverse(tree);
                }
              }
              warningsSetter.traverse(unit.body)
            })
          }
        }
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