package org.scalaide.sbt.core

import java.io.File

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IProjectDescription
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IWorkspace
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.SubMonitor
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.internal.core.ClasspathEntry
import org.eclipse.jdt.ui.PreferenceConstants
import org.scalaide.core.SdtConstants
import org.scalaide.sbt.util.PicklingUtils._

import sbt.protocol.Attributed

object SbtProjectSupport {

  def createWorkspaceProject(build: SbtBuild, projectName: String, builderName: String, monitor: IProgressMonitor)(implicit ctx: ExecutionContext): Future[IProject] = {
    val progress = SubMonitor.convert(monitor, 100)

    // create the project in the workspace
    progress.beginTask(s"$projectName: create project", 10)
    val workspace = ResourcesPlugin.getWorkspace
    val project = workspace.getRoot.getProject(projectName)

    // create project as Java only
    progress.beginTask(s"$projectName: Initialise project", 20)
    val description = createJavaProjectDescription(build, projectName, builderName, workspace)
    val res = description.map { d =>
      project.create(d, IResource.NONE, monitor)
      project.open(monitor)
    }.flatMap { _ =>
      // configure the classpath
      progress.beginTask(s"$projectName: configure classpath", 40)
      configureClassPath(build, projectName, project, monitor)
    }.map { _ =>
      // add the Scala nature
      progress.beginTask(s"$projectName: finalize configuration", 20)
      val description = project.getDescription()
      description.setNatureIds(Array(SdtConstants.NatureId, JavaCore.NATURE_ID))
      project.setDescription(description, IResource.FORCE, monitor)
      // sync all data back to the file system
      progress.beginTask(s"$projectName: sync data", 20)
      project.refreshLocal(IResource.DEPTH_INFINITE, progress)
      project
    }

    // in any case, terminate the progress monitor
    res.onComplete { _ =>
      progress.done()
    }

    res
  }

  private def createJavaProjectDescription(build: SbtBuild, projectName: String, builderName: String, workspace: IWorkspace)(implicit ctx: ExecutionContext): Future[IProjectDescription] = {
    for {
      projectRoot <- build.setting[File](projectName, "baseDirectory")
    } yield {
      val description = workspace.newProjectDescription(projectName)

      description.setLocation(new Path(projectRoot.getAbsolutePath()))
      description.setNatureIds(Array(JavaCore.NATURE_ID))

      val newBuilderCommand = description.newCommand;
      newBuilderCommand.setBuilderName(builderName);
      description.setBuildSpec(Array(newBuilderCommand))

      description
    }
  }

  private def configureClassPath(build: SbtBuild, projectName: String, project: IProject, monitor: IProgressMonitor)(implicit ctx: ExecutionContext): Future[Unit] = {
    val srcDirs = build.setting[Seq[File]](projectName, "sourceDirectories", Some("compile"))
    val clsDirs = build.setting[File](projectName, "classDirectory", Some("compile"))
    val classpath = build.setting[Seq[Attributed[File]]](projectName, "externalDependencyClasspath", Some("compile"))
    val testSrcDirs = build.setting[Seq[File]](projectName, "sourceDirectories", Some("test"))
    val testClsDirs = build.setting[File](projectName, "classDirectory", Some("test"))
    val testClasspath = build.setting[Seq[Attributed[File]]](projectName, "externalDependencyClasspath", Some("test"))

    for {
      srcDir <- srcDirs
      clsDir <- clsDirs
      cp <- classpath
      testSrcDir <- testSrcDirs
      testClsDir <- testClsDirs
      testCp <- testClasspath
    } yield {
      val javaProject = JavaCore.create(project)

      val compileClassOutput = pathInProject(project, clsDir)
      val sourcePaths = srcDir.filter(_.exists).map { d =>
        JavaCore.newSourceEntry(pathInProject(project, d), ClasspathEntry.EXCLUDE_NONE, compileClassOutput)
      }

      val testClassOutput = pathInProject(project, testClsDir)
      val testSourcePaths = testSrcDir.filter(_.exists).map { d =>
        JavaCore.newSourceEntry(pathInProject(project, d), ClasspathEntry.EXCLUDE_NONE, testClassOutput)
      }

      val fullClasspath = (cp ++ testCp).distinct

      val referencedJars = fullClasspath.flatMap(cp ⇒ createEclipseClasspathEntry(cp.data))

      val classpath = sourcePaths ++ testSourcePaths ++ referencedJars ++ PreferenceConstants.getDefaultJRELibrary()

      javaProject.setRawClasspath(classpath.toArray, monitor)
    }
  }

  private val ScalaLibraryRegex = ".*org\\.scala-lang/scala-library.*".r
  private val ScalaCompilerRegex = ".*org\\.scala-lang/scala-compiler.*".r
  private val ScalaReflectRegex = ".*org\\.scala-lang/scala-reflect.*".r

  private def createEclipseClasspathEntry(file: File): Option[IClasspathEntry] = {
    file.getAbsolutePath() match {
      case ScalaLibraryRegex() =>
        Some(JavaCore.newContainerEntry(Path.fromPortableString(SdtConstants.ScalaLibContId)))
      case ScalaCompilerRegex() =>
        Some(JavaCore.newContainerEntry(Path.fromPortableString(SdtConstants.ScalaCompilerContId)))
      case ScalaReflectRegex() =>
        None
      case path =>
        Some(JavaCore.newLibraryEntry(Path.fromOSString(path), null, null))
    }
  }

  private def pathInProject(project: IProject, file: File): IPath = {
    project.getFullPath().append(Path.fromOSString(file.getAbsolutePath()).makeRelativeTo(project.getLocation()))
  }

}
