package org.scalaide.core.internal.launching

import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.Status
import org.scalaide.core.IScalaProject
import org.scalaide.core.SdtConstants
import org.scalaide.ui.internal.handlers.CompilerLaunchErrorHandler
import org.scalaide.util.eclipse.EclipseUtils

object MainClassVerifier {
  private final val ModuleClassSuffix = "$"
}

class MainClassVerifier {
  /**
   * Performs the following checks:
   *
   * 1) If the classfile of the fully-qualified `mainTypeName` can be found in the `project`'s output folders, a matching companion classfile
   *    (which is expected to contain the main method) is also found. If it can't be found, an error is reported (this is done because it means
   *    the user is trying to run a plain `class`, instead of an `object`).
   *
   * 2) If the class of the fully-qualified `mainTypeName` cannot be found, it reports an error if the `project` has build errors. Otherwise, it
   *    trusts the user's configuration and returns ok (this is the case for instance when the `mainTypeName` comes from a classfile in a JAR).
   *
   * @param project      The scala project containing the main type.
   * @param mainTypeName The fully-qualified main type name.
   * @param hasBuildErrors True if the passed `project` has build errors, false otherwise.
   */
  def execute(project: IScalaProject, mainTypeName: String, hasBuildErrors: Boolean): IStatus = {
    canRunMain(project, mainTypeName, hasBuildErrors)
  }

  private def canRunMain(project: IScalaProject, mainTypeName: String, hasBuildErrors: Boolean): IStatus = {
    val mainClass = findClassFile(project, mainTypeName)
    def mainModuleClass = findClassFile(project, mainTypeName + MainClassVerifier.ModuleClassSuffix)

    if (mainClass.nonEmpty && mainModuleClass.isEmpty)
      new Status(IStatus.ERROR, SdtConstants.PluginId, CompilerLaunchErrorHandler.STATUS_CODE_LAUNCH_ERROR, s"${mainTypeName} needs to be an `object` (it is currently a `class`).", null)
    else if (hasBuildErrors) {
      val msg = s"Project ${project.underlying.getName} contains build errors."
      new Status(IStatus.ERROR, SdtConstants.PluginId, CompilerLaunchErrorHandler.STATUS_CODE_LAUNCH_ERROR, msg, null)
    } else new Status(IStatus.OK, SdtConstants.PluginId, "")
  }

  private def findClassFile(project: IScalaProject, mainTypeName: String): Option[IResource] = {
    val outputLocations = project.outputFolders
    val classFileName = mainTypeName.replace('.', '/')
    (for {
      outputLocation <- outputLocations
      classFileLocation = outputLocation.append(s"${classFileName}.class")
      classFile <- Option(EclipseUtils.workspaceRoot.findMember(classFileLocation))
    } yield classFile).headOption
  }
}
