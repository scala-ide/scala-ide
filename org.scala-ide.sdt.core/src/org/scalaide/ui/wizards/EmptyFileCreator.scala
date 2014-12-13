package org.scalaide.ui.wizards

import org.eclipse.core.resources.IContainer
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.Path

/**
 * File creator that can only create empty files.
 */
class EmptyFileCreator extends FileCreator {

  override def create(folder: IContainer, name: String): IFile =
    folder.getFile(new Path(name))

  override def validateName(folder: IContainer, name: String): Validation =
    if (folder.getFile(new Path(name)).exists())
      Invalid("File already exists")
    else
      Valid

  override def initialPath(res: IResource): String =
    ""

  override def completionEntries(folder: IContainer, name: String): Seq[String] =
    Seq()

  override def templateVariables(folder: IContainer, name: String): Map[String, String] =
    Map()

}
