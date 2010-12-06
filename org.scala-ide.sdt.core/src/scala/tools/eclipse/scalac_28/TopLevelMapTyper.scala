/**
 * 
 */
package scala.tools.eclipse

package scalac_28

import scala.tools.eclipse.internal.logging.Tracer
import scala.tools.eclipse.util.EclipseFile

/**
 * @TODO update content on file change, several possibilities eg :
 *   * via hook on ScalaSourceFileEditor.doSave(pm: IProgressMonitor) (original solution)
 *   * via an hook on incremental builder
 *   * via a resourceListener (see http://www.eclipse.org/articles/Article-Resource-deltas/resource-deltas.html)
 *   * via a hook on hosted Compiler when it compile successfully
 *   In every case of hook, listener don't forgot to unregister (avoid memory leak)
 *     
 * @author david.bernard
 * @see https://www.assembla.com/code/scala-ide/git/changesets/623ff3bc19cfb0e661ec11d01a28f2133f00eae4
 */

trait TopLevelMapTyper extends ScalaPresentationCompiler {
  self => ScalaPresentationCompiler //TODO search and replace by the most low level type eg interactive.Global
  
  def project : ScalaProject
  
  class EclipseTyperRun extends TyperRun {
    val topLevelMap : TopLevelMap = {
      Tracer.timeOf("Building top-level map for: "+ project.underlying.getName) {
        new TopLevelMap().resetWith(project.allSourceFiles)
      }
    }
    
    def findSource(qualifiedName : String) = topLevelMap.get(qualifiedName)
    
    override def compileSourceFor(context : Context, name : Name) = {
      def addImport(imp : analyzer.ImportInfo) = {
        val qual = imp.qual
        val sym = qual.symbol
        sym.isPackage && {
          var selectors = imp.tree.selectors
          if (selectors.head.name == name.toTermName)
            compileSourceFor(sym.fullName+"."+name)
          else if (selectors.head.name == nme.WILDCARD)
            compileSourceFor(sym.fullName+"."+name)
          else
            false
        }
      }
      
      context.imports.exists(addImport) || {
        val pkg = context.owner.enclosingPackage
        compileSourceFor(pkg.fullName+"."+name)
      }
    }

    override def compileSourceFor(qual : Tree, name : Name) = {
      val sym = qual.symbol
      sym != null && sym.isPackage && compileSourceFor(sym.fullName+"."+name)
    }
    
    def compileSourceFor(qualifiedName : String) : Boolean = {
      //Tracer.println("call compileSourceFor : " + qualifiedName)  
      findSource(qualifiedName) match {
        case Some(iFile) if (!project.isStandardSource(iFile, qualifiedName)) =>
          val file = new EclipseFile(iFile)
          if (compiledFiles contains file.path)
            false
          else {
            Tracer.println("Adding: "+file+" to resolve: "+qualifiedName)
            compileLate(file)
            true
          }
        case _ => false
      }
    }
  }
  
  override def newTyperRun = new EclipseTyperRun
}
