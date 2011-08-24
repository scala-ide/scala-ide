package scala.tools.eclipse
package buildmanager
package sbtintegration

import org.eclipse.core.runtime.SubMonitor
import org.eclipse.core.resources.{ IncrementalProjectBuilder, IProject}
import scala.tools.eclipse.javaelements.JDTUtils
import scala.tools.eclipse.util.FileUtils

import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.jdt.internal.core.builder.{ JavaBuilder, NameEnvironment, State }


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