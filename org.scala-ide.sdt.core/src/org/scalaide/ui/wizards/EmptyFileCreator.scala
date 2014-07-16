package org.scalaide.ui.wizards

import org.eclipse.core.resources.IFolder
import org.eclipse.core.resources.IResource
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.IPath
import org.scalaide.util.internal.eclipse.FileUtils

/**
 * File creator that can only create empty files.
 */
class EmptyFileCreator extends FileCreator {

  override def createFilePath(folder: IFolder, name: String): IPath = {
    val root = ResourcesPlugin.getWorkspace().getRoot()
    root.getRawLocation().append(folder.getFullPath()).append(name)
  }

  override def validateName(folder: IFolder, name: String): Validation =
    if (FileUtils.existsWorkspaceFile(folder.getFullPath().append(name)))
      Invalid("File already exists")
    else
      Valid

  override def initialPath(res: IResource): String =
    ""

  override def completionEntries(folder: IFolder, name: String): Seq[String] =
    Seq()

  override def templateVariables(folder: IFolder, name: String): Map[String, String] =
    Map()

}