package org.scalaide.core.internal.builder.zinc

import java.io.File

import scala.tools.eclipse.contribution.weaving.jdt.jcompiler.BuildManagerStore

import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IncrementalProjectBuilder.INCREMENTAL_BUILD
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.SubMonitor
import org.eclipse.jdt.core.IJavaModelMarker
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.internal.builder.JDTBuilderFacade
import org.scalaide.util.eclipse.FileUtils

import xsbti.Logger
import xsbti.Reporter
import xsbti.compile.JavaCompiler
import xsbti.compile.Output

/**
 * Eclipse Java compiler interface, used by the SBT builder.
 *  This class forwards to the internal Eclipse Java compiler, using
 *  reflection to circumvent private/protected modifiers.
 */
class JavaEclipseCompiler(p: IProject, monitor: SubMonitor) extends JavaCompiler with JDTBuilderFacade {

  override def project = p

  override def compile(sources: Array[File], classpath: Array[File], output: Output, options: Array[String], log: Logger): Unit = {
    val scalaProject = IScalaPlugin().getScalaProject(project)

    val allSourceFiles = scalaProject.allSourceFiles()
    val depends = scalaProject.directDependencies
    if (allSourceFiles.exists(FileUtils.hasBuildErrors(_)))
      depends.toArray
    else {
      ensureProject

      // refresh output directories, since SBT removes classfiles that the Eclipse
      // Java compiler expects to find
      for (folder <- scalaProject.outputFolders) {
        val container =
          if (project.getFullPath == folder)
            project
          else
            ResourcesPlugin.getWorkspace().getRoot().getFolder(folder)
        container.refreshLocal(IResource.DEPTH_INFINITE, null)
      }

      BuildManagerStore.INSTANCE.setJavaSourceFilesToCompile(sources, project)
      try
        scalaJavaBuilder.build(INCREMENTAL_BUILD, new java.util.HashMap(), monitor)
      finally
        BuildManagerStore.INSTANCE.setJavaSourceFilesToCompile(null, project)

      refresh()
    }
  }

  override def compileWithReporter(sources: Array[File], classpath: Array[File], output: Output, options: Array[String], reporter: Reporter, log: Logger): Unit = {
    compile(sources, classpath, output, options, log)
    Option(reporter).collect {
      case reporter: SbtBuildReporter =>
        val javaProblems: Seq[IMarker] = project.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)
        javaProblems.foreach { marker =>
          reporter.riseJavaErrorOrWarning(marker.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO))
        }
    }
  }
}
