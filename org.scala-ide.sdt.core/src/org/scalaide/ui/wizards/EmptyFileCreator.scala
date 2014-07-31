package org.scalaide.ui.wizards

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IFolder
import org.eclipse.core.resources.IResource

/**
 * File creator that can only create empty files.
 */
class EmptyFileCreator extends FileCreator {

  override def create(folder: IFolder, name: String): IFile =
    folder.getFile(name)

  override def validateName(folder: IFolder, name: String): Validation =
    if (folder.getFile(name).exists())
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