package org.scalaide.sbt.core

import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IProjectDescription
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.IWorkspace
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.SubMonitor
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.internal.core.ClasspathEntry
import org.eclipse.jdt.ui.PreferenceConstants
import sbt.Attributed
import org.eclipse.jdt.core.IClasspathEntry

object SbtProjectSupport {

  def createWorkspaceProject(build: SbtBuild, projectName: String, builderName: String, monitor: IProgressMonitor): Future[IProject] = {

    val progress = SubMonitor.convert(monitor, 100);

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
      description.setNatureIds(Array("org.scala-ide.sdt.core.scalanature", "org.eclipse.jdt.core.javanature"))
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

  private def createJavaProjectDescription(build: SbtBuild, projectName: String, builderName: String, workspace: IWorkspace): Future[IProjectDescription] = {
    for {
      projectRoot <- build.getSettingValue[File](projectName, "baseDirectory")
    } yield {
      val description = workspace.newProjectDescription(projectName)

      description.setLocation(new Path(projectRoot.getAbsolutePath()))
      description.setNatureIds(Array("org.eclipse.jdt.core.javanature"))

      val newBuilderCommand = description.newCommand;
      newBuilderCommand.setBuilderName(builderName);
      description.setBuildSpec(Array(newBuilderCommand))

      description
    }
  }

  private def configureClassPath(build: SbtBuild, projectName: String, project: IProject, monitor: IProgressMonitor): Future[Unit] = {
    val compileSourceDirectoriesFuture = build.getSettingValue[Seq[File]](projectName, "sourceDirectories", Some("compile"))
    val compileClassDirectioryFuture = build.getSettingValue[File](projectName, "classDirectory", Some("compile"))
    val compileClasspathFuture = build.getSettingValue[Seq[Attributed[File]]](projectName, "externalDependencyClasspath", Some("compile"))
    val testSourceDirectoriesFuture = build.getSettingValue[Seq[File]](projectName, "sourceDirectories", Some("test"))
    val testClassDirectoryFuture = build.getSettingValue[File](projectName, "classDirectory", Some("test"))
    val testClasspathFuture = build.getSettingValue[Seq[Attributed[File]]](projectName, "externalDependencyClasspath", Some("test"))

    for {
      compileSourceDirectories <- compileSourceDirectoriesFuture
      compileClassDirectory <- compileClassDirectioryFuture
      compileClasspath <- compileClasspathFuture
      testSourceDirectories <- testSourceDirectoriesFuture
      testClassDirectory <- testClassDirectoryFuture
      testClasspath <- testClasspathFuture
    } yield {
      val javaProject = JavaCore.create(project)

      val compileClassOutput = pathInProject(project, compileClassDirectory)
      val sourcePaths = compileSourceDirectories.filter(_.exists).map { d =>
        JavaCore.newSourceEntry(pathInProject(project, d), ClasspathEntry.EXCLUDE_NONE, compileClassOutput)
      }

      val testClassOutput = pathInProject(project, testClassDirectory)
      val testSourcePaths = testSourceDirectories.filter(_.exists).map { d =>
        JavaCore.newSourceEntry(pathInProject(project, d), ClasspathEntry.EXCLUDE_NONE, testClassOutput)
      }

      val fullClasspath = (compileClasspath ++ testClasspath).distinct

      val referencedJars = fullClasspath.flatMap(j => createEclipseClasspathEntry(j.data))

      val classpath = sourcePaths ++ testSourcePaths ++ referencedJars ++ PreferenceConstants.getDefaultJRELibrary()

      javaProject.setRawClasspath(classpath.toArray, monitor)
    }
  }

  val ScalaLibraryRegex = ".*org\\.scala-lang/scala-library.*".r
  val ScalaCompilerRegex = ".*org\\.scala-lang/scala-compiler.*".r
  val ScalaReflectRegex = ".*org\\.scala-lang/scala-reflect.*".r

  def createEclipseClasspathEntry(file: File): Option[IClasspathEntry] = {
    val path = file.getAbsolutePath()
    path match {
      case ScalaLibraryRegex() =>
        Some(JavaCore.newContainerEntry(Path.fromPortableString("org.scala-ide.sdt.launching.SCALA_CONTAINER")))
      case ScalaCompilerRegex() =>
        Some(JavaCore.newContainerEntry(Path.fromPortableString("org.scala-ide.sdt.launching.SCALA_COMPILER_CONTAINER")))
      case ScalaReflectRegex() =>
        None
      case _ =>
        Some(JavaCore.newLibraryEntry(Path.fromOSString(path), null, null))
    }
  }

  private def pathInProject(project: IProject, file: File) = {
    project.getFullPath().append(Path.fromOSString(file.getAbsolutePath()).makeRelativeTo(project.getLocation()))
  }

}