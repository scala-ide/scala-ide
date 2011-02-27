/**
 *
 */
package scala.tools.eclipse
package scalac_28

import scala.tools.eclipse.util.Tracer
import scala.tools.eclipse.util.EclipseFile
import org.eclipse.core.runtime.Path
import org.eclipse.core.resources.IFile

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

  private val topLevelMap : TopLevelMap  = {
    Tracer.timeOf("Building top-level map for: "+ project.underlying.getName) {
      new TopLevelMap().resetWith(project.allSourceFiles)
    }
  }
  private val sourceFolders = project.sourceFolders

  class EclipseTyperRun extends TyperRun {

    def findSource(qualifiedName : String) = topLevelMap.get(qualifiedName)

    def isStandardSource(file : IFile, qualifiedName : String) : Boolean = {
      val pathString = file.getLocation.toString
      val suffix = qualifiedName.replace(".", "/")+".scala"
      pathString.endsWith(suffix) && {
        val suffixPath = new Path(suffix)
        val sourceFolderPath = file.getLocation.removeLastSegments(suffixPath.segmentCount)
        sourceFolders.exists(_ == sourceFolderPath)
      }
    }

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
      //for a name X, compileSourceFor can be called for
      // scala.X, java.lang.X, current.package.X

      //Tracer.println("call compileSourceFor : " + qualifiedName)
      findSource(qualifiedName) match {
        case Some(iFile) if (!isStandardSource(iFile, qualifiedName)) => {
          val file = new EclipseFile(iFile)
          if (compiledFiles contains file.path)
            false
          else {
            Tracer.println("Adding (" + compiledFiles.size + ") : "+file+" to resolve: "+qualifiedName)
            compileLate(file)
            true
          }
        }
        case Some(iFile) => {
          Tracer.println("Ignoring: "+ iFile+" to resolve: "+qualifiedName)
          false
        }
        case None => {
          //Tracer.println("Not found file to resolve: "+qualifiedName)
          false
        }
      }
    }
  }

  override def newTyperRun = {
    Tracer.println("newTyperRun")
    //COMPAT-2.9 currentTyperRun is private
    //currentTyperRun = new EclipseTyperRun()
    // throw an exception if method not found
    //I don't use getDeclaredMethod("currentTyperRun_$eq") to only match on the name regardless the Type (path dependends Type)
    val methods = classOf[scala.tools.nsc.interactive.Global].getDeclaredMethods()
    val setter = methods.find(_.getName == "currentTyperRun_$eq")
    setter match {
      case Some(m) => m.invoke(this, new EclipseTyperRun())
      case None => throw new NoSuchMethodException("TopLevelMapTyper.currentTyperRun_=(TyperRun)")
    }
  }
}
