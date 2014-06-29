package org.scalaide.ui.wizards

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource

/**
 * File creator that can only create empty files. It is based on
 * `ScalaFileCreator` because it defines useful initial path and validation
 * logic.
 */
class EmptyFileCreator extends ScalaFileCreator {

  override def validateName(project: IProject, name: String): Validation =
    checkFileExists(project, name)

  override def initialPath(res: IResource): String =
    generateInitialPath(
        path = res.getFullPath().segments(),
        srcDirs = Nil,
        isDirectory = res.getType() == IResource.FOLDER)

  override def initialPathAfterProjectSelection(project: IProject): String =
    ""

  override def templateVariables(project: IProject, name: String): Map[String, String] =
    Map()
}