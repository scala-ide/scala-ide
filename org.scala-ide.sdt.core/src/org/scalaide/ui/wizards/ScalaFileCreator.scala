package org.scalaide.ui.wizards

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IPath
import org.scalaide.util.internal.eclipse.ProjectUtils

trait ScalaFileCreator extends FileCreator {
  import ProjectUtils._

  override def templateVariables(project: IProject, name: String): Map[String, String] = {
    ???
  }

  override def initialPath(project: IResource): String = {
    val srcDirs = sourceDirs(project.getProject()).map(_.lastSegment())
    generateInitialPath(
        path = project.getFullPath().segments(),
        srcDirs = srcDirs,
        isDirectory = project.getType() == IResource.FOLDER)
  }

  override def validateName(project: IProject, name: String): Validation = {
    ???
  }

  override def createFileFromName(project: IProject, name: String): IPath = {
    ???
  }

  /**
   * `path` contains the path starting from the project to a given element.
   * `srcFolders` contains the names of all source folders of a given project.
   * `isDirectory` describes if the last element of `fullPath` references a
   * directory.
   */
  private[wizards] def generateInitialPath(path: Seq[String], srcDirs: Seq[String], isDirectory: Boolean): String = {
    if (path.size < 3)
      ""
    else {
      val Seq(project, topFolder, rawSubPath @ _*) = path
      val subPath = if (isDirectory) rawSubPath else rawSubPath.init

      def generatePath(delimiter: String) = {
        val p = subPath.mkString(delimiter)

        if (p.isEmpty())
          s"$topFolder/"
        else
          s"$topFolder/$p$delimiter"
      }

      generatePath(if (srcDirs contains topFolder) "." else "/")
    }
  }
}
