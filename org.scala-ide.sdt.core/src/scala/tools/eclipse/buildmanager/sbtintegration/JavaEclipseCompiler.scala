package scala.tools.eclipse
package buildmanager
package sbtintegration

import org.eclipse.core.runtime.SubMonitor
import org.eclipse.core.resources.{ IncrementalProjectBuilder, IProject}
import scala.tools.eclipse.javaelements.JDTUtils
import scala.tools.eclipse.util.FileUtils
import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.jdt.internal.core.builder.{ JavaBuilder, State }
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.resources.IResource

/** Eclipse Java compiler interface, used by the SBT builder.
 *  This class forwards to the internal Eclipse Java compiler, using
 *  reflection to circumvent private/protected modifiers.
 */
class JavaEclipseCompiler(project: IProject, monitor: SubMonitor) {
  private val scalaJavaBuilder = new GeneralScalaJavaBuilder
  
  def plugin = ScalaPlugin.plugin
  def getProject = project
  
  def build(kind: Int): Array[IProject] = {
    
    
    val project = plugin.getScalaProject(getProject)
    
    val allSourceFiles = project.allSourceFiles()
    val depends = project.externalDepends.toList.toArray
    if (allSourceFiles.exists(FileUtils.hasBuildErrors(_)))
      depends
    else {
      ensureProject
      
      // refresh output directories, since SBT removes classfiles that the Eclipse
      // Java compiler expects to find
      for (folder <- project.outputFolders) {
        val container = ResourcesPlugin.getWorkspace().getRoot().getFolder(folder)
        container.refreshLocal(IResource.DEPTH_INFINITE, null)
      }
      
      val javaDepends = scalaJavaBuilder.build(kind,new java.util.HashMap(), monitor) 
      val modelManager = JavaModelManager.getJavaModelManager
      val state = modelManager.getLastBuiltState(getProject, null).asInstanceOf[State]
      val newState = if (state ne null) state
        else {
          ScalaJavaBuilderUtils.initializeBuilder(scalaJavaBuilder, 0, false)
          StateUtils.newState(scalaJavaBuilder)
        }
      StateUtils.tagAsStructurallyChanged(newState)
      StateUtils.resetStructurallyChangedTypes(newState)
      modelManager.setLastBuiltState(getProject, newState)
      JDTUtils.refreshPackageExplorer
      (Set.empty ++ depends ++ javaDepends).toArray
    }
  }
    
  def ensureProject = {
    if (scalaJavaBuilder.getProject == null)
      scalaJavaBuilder.setProject0(getProject)
  }
}