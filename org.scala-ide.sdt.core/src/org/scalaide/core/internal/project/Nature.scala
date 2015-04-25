package org.scalaide.core.internal.project

import scala.collection.mutable.ArrayBuffer
import org.eclipse.core.resources.ICommand
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IProjectNature
import org.eclipse.core.resources.IResource
import org.eclipse.jdt.core.IClasspathEntry
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.launching.JavaRuntime
import org.eclipse.core.runtime.Path
import org.scalaide.core.SdtConstants
import org.scalaide.util.eclipse.EclipseUtils

object Nature {

  def removeScalaLib(jp: IJavaProject) {
    val scalaLibPath = Path.fromPortableString(SdtConstants.ScalaLibContId)
    val buf = jp.getRawClasspath filter (_.getPath!= scalaLibPath)
    jp.setRawClasspath(buf, null)
  }

  /**
   * Removes any existing scala library from the classpath, and adds the ScalaPlugin.scalaLibId
   * library container to the classpath. Saves the project settings of `jp`.
   */
  def addScalaLibAndSave(project: IProject) {
    val jp = JavaCore.create(project)
    Nature.removeScalaLib(jp)

    // Put the Scala classpath container before JRE container
    val buf = ArrayBuffer(jp.getRawClasspath : _*)
    val scalaLibEntry = JavaCore.newContainerEntry(Path.fromPortableString(SdtConstants.ScalaLibContId))
    val jreIndex = buf.indexWhere(_.getPath.toPortableString.startsWith(JavaRuntime.JRE_CONTAINER))
    if (jreIndex != -1) {
      buf.insert(jreIndex, scalaLibEntry)
    } else {
      buf += scalaLibEntry
      buf += JavaCore.newContainerEntry(Path.fromPortableString(JavaRuntime.JRE_CONTAINER))
    }
    jp.setRawClasspath(buf.toArray, null)

    jp.save(null, true)
  }
}

class Nature extends IProjectNature {
  private var project : IProject = _

  override def getProject = project
  override def setProject(project : IProject) = this.project = project

  override def configure() {
    if (project == null || !project.isOpen)
      return

    updateBuilders(project, List(JavaCore.BUILDER_ID), SdtConstants.BuilderId)

    EclipseUtils.withSafeRunner("Error occurred while trying to add Scala library to classpath") {
      Nature.addScalaLibAndSave(getProject)
    }
  }

  override def deconfigure() {
    if (project == null || !project.isOpen)
      return

    updateBuilders(project, List(SdtConstants.BuilderId), JavaCore.BUILDER_ID)

    EclipseUtils.withSafeRunner("Error occurred while trying to remove Scala library from classpath") {
      val jp = JavaCore.create(getProject)
      Nature.removeScalaLib(jp)
      jp.save(null, true)
    }
  }

  private def updateBuilders(project: IProject, buildersToRemove: List[String], builderToAdd: String) {
    EclipseUtils.withSafeRunner(s"Error occurred while trying to update builder of project '$project'") {
      val description = project.getDescription
      val previousCommands = description.getBuildSpec
      val filteredCommands = previousCommands.filterNot(buildersToRemove contains _.getBuilderName)
      val newCommands = if (filteredCommands.exists(_.getBuilderName == builderToAdd))
        filteredCommands
      else
        filteredCommands :+ {
          val newBuilderCommand = description.newCommand;
          newBuilderCommand.setBuilderName(builderToAdd);
          newBuilderCommand
        }
      description.setBuildSpec(newCommands)
      project.setDescription(description, IResource.FORCE, null)
    }
  }
}
