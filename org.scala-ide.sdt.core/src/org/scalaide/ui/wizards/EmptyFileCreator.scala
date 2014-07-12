package org.scalaide.ui.wizards

import org.eclipse.core.resources.IFolder
import org.eclipse.core.resources.IResource

/**
 * File creator that can only create empty files. It is based on
 * `ScalaFileCreator` because it defines some useful validation and file
 * creation logic.
 */
class EmptyFileCreator extends ScalaFileCreator {

  override def validateName(folder: IFolder, name: String): Validation =
    checkFileExists(folder, name)

  override def initialPath(res: IResource): String =
    generateInitialPath(
        path = res.getFullPath().segments(),
        isDirectory = res.getType() == IResource.FOLDER)

  override def completionEntries(folder: IFolder, name: String): Seq[String] =
    Seq()

  override def templateVariables(folder: IFolder, name: String): Map[String, String] =
    Map()

  private[wizards] def generateInitialPath(path: Seq[String], isDirectory: Boolean): String = {
    if (path.size < 3)
      ""
    else {
      val Seq(_, rawSubPath @ _*) = path
      val subPath = if (isDirectory) rawSubPath else rawSubPath.init
      val p = subPath.mkString("/")

      if (p.isEmpty()) "" else s"$p/"
    }
  }
}