package scala.tools.eclipse
package buildmanager
package sbtintegration

import java.io.File
import org.eclipse.core.runtime.SubMonitor
import org.eclipse.core.resources.{ IncrementalProjectBuilder, IProject}
import scala.tools.eclipse.javaelements.JDTUtils
import scala.tools.eclipse.util.FileUtils
import scala.tools.eclipse.contribution.weaving.jdt.jcompiler.BuildManagerStore
import org.eclipse.jdt.internal.core.JavaModelManager
import org.eclipse.jdt.internal.core.builder.{ JavaBuilder, State }
import org.eclipse.core.resources.IncrementalProjectBuilder.INCREMENTAL_BUILD
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.resources.IResource
import xsbti.compile.{JavaCompiler, Output}
import xsbti.Logger

/** Eclipse Java compiler interface, used by the SBT builder.
 *  This class forwards to the internal Eclipse Java compiler, using
 *  reflection to circumvent private/protected modifiers.
 */
class JavaEclipseCompiler(project: IProject, monitor: SubMonitor) extends JavaCompiler {
  private val scalaJavaBuilder = new GeneralScalaJavaBuilder

  def plugin = ScalaPlugin.plugin

  def compile(sources: Array[File], classpath: Array[File], output: Output, options: Array[String], log: Logger) {
    val scalaProject = plugin.getScalaProject(project)

    val allSourceFiles = scalaProject.allSourceFiles()
    val depends = scalaProject.directDependencies
    if (allSourceFiles.exists(FileUtils.hasBuildErrors(_)))
      depends.toArray
    else {
      ensureProject

      // refresh output directories, since SBT removes classfiles that the Eclipse
      // Java compiler expects to find
      for (folder <- scalaProject.outputFolders) {
        val container = ResourcesPlugin.getWorkspace().getRoot().getFolder(folder)
        container.refreshLocal(IResource.DEPTH_INFINITE, null)
      }

      BuildManagerStore.INSTANCE.setJavaSourceFilesToCompile(sources, project)
      try
        scalaJavaBuilder.build(INCREMENTAL_BUILD, new java.util.HashMap(), monitor)
      finally
        BuildManagerStore.INSTANCE.setJavaSourceFilesToCompile(null, project)

      val modelManager = JavaModelManager.getJavaModelManager
      val state = modelManager.getLastBuiltState(project, null).asInstanceOf[State]
      val newState = if (state ne null) state
        else {
          ScalaJavaBuilderUtils.initializeBuilder(scalaJavaBuilder, 0, false)
          StateUtils.newState(scalaJavaBuilder)
        }
      StateUtils.tagAsStructurallyChanged(newState)
      StateUtils.resetStructurallyChangedTypes(newState)
      modelManager.setLastBuiltState(project, newState)
      JDTUtils.refreshPackageExplorer
    }
  }

  def ensureProject = {
    if (scalaJavaBuilder.getProject == null)
      scalaJavaBuilder.setProject0(project)
  }
}
