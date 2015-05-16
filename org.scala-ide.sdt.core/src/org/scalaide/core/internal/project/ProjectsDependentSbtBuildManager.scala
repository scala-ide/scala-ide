package org.scalaide.core.internal.project

import scala.tools.nsc.Settings

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.SubMonitor
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.IScalaProject
import org.scalaide.core.SdtConstants
import org.scalaide.core.internal.builder.BuildProblemMarker
import org.scalaide.core.internal.builder.zinc.EclipseSbtBuildManager
import org.scalaide.ui.internal.preferences.ScalaPluginSettings
import org.scalaide.util.internal.SettingConverterUtil

/** Build manager which compiles sources without dividing on scopes.
 *  Refer to [[CompileScope]]
 */
class ProjectsDependentSbtBuildManager(project: IScalaProject, settings: Settings)
    extends EclipseSbtBuildManager(project, settings) {
  private def areTransitiveDependenciesBuilt = {
    val projectsInError =
      project.transitiveDependencies.filter(p => IScalaPlugin().getScalaProject(p).buildManager.hasErrors)

    val stopBuildOnErrorsProperty = SettingConverterUtil.convertNameToProperty(ScalaPluginSettings.stopBuildOnErrors.name)
    val stopBuildOnErrors = project.storage.getBoolean(stopBuildOnErrorsProperty)

    if (stopBuildOnErrors && projectsInError.nonEmpty) {
      project.underlying.deleteMarkers(SdtConstants.ProblemMarkerId, true, IResource.DEPTH_INFINITE)
      val errorText = projectsInError.map(_.getName).toSet.mkString(", ")
      BuildProblemMarker.create(project.underlying,
        s"Project ${project.underlying.getName} not built due to errors in dependent project(s) ${errorText}")
      false
    } else true
  }

  override def build(addedOrUpdated: Set[IFile], removed: Set[IFile], pm: SubMonitor): Unit = {
    if (areTransitiveDependenciesBuilt) {
      project.underlying.deleteMarkers(SdtConstants.ProblemMarkerId, true, IResource.DEPTH_INFINITE)
      super.build(addedOrUpdated, removed, pm)
    }
  }
}