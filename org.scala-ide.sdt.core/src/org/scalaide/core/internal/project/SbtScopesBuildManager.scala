package org.scalaide.core.internal.project

import scala.tools.nsc.Settings
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.SubMonitor
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.IScalaProject
import org.scalaide.core.SdtConstants
import org.scalaide.core.internal.builder.BuildProblemMarker
import org.scalaide.core.internal.builder.EclipseBuildManager
import org.scalaide.core.internal.project.scopes.BuildScopeUnit
import org.scalaide.ui.internal.preferences.ScalaPluginSettings
import org.scalaide.util.internal.SettingConverterUtil
import sbt.inc.Analysis
import sbt.inc.IncOptions
import java.io.File

/** Manages of source compilation for all scopes.
 *  Refer to [[CompileScope]]
 */
class SbtScopesBuildManager(val owningProject: IScalaProject, managerSettings: Settings)
    extends EclipseBuildManager {
  val DefaultScopesOrder: Seq[CompileScope] = Seq(CompileMacrosScope, CompileMainScope, CompileTestsScope)

  private val buildScopeUnits = DefaultScopesOrder.foldLeft(List.empty[BuildScopeUnit]) { (acc, scope) =>
    new BuildScopeUnit(scope, owningProject, managerSettings, acc.toSeq) :: acc
  }.reverse

  /** Says about errors in specific scope. */
  def hasErrors(compileScope: CompileScope): Boolean =
    buildScopeUnits filter { _.scope == compileScope } exists { _.hasErrors }

  override def hasErrors: Boolean = hasInternalErrors && (buildScopeUnits exists { _.hasErrors })

  override def build(addedOrUpdated: Set[IFile], removed: Set[IFile], monitor: SubMonitor): Unit = {
    owningProject.underlying.deleteMarkers(SdtConstants.ProblemMarkerId, true, IResource.DEPTH_INFINITE)
    val scopesAndProjectsInError = buildScopeUnits.map { unit =>
      ScopeUnitWithProjectsInError(unit, findProjectsInError(unit))
    }
    scopesAndProjectsInError.foreach { scopePotentiallyToRebuild =>
      putMarkersForTransitives(scopePotentiallyToRebuild)
      if (!(scopePotentiallyToRebuild.projectsInError.nonEmpty && isStopBuildOnErrors)) {
        val scopeUnit = scopePotentiallyToRebuild.owner
        scopeUnit.build(addedOrUpdated, removed, monitor)
      }
    }
    hasInternalErrors = scopesAndProjectsInError.exists { scopeWithErrors =>
      scopeWithErrors.owner.hasErrors || scopeWithErrors.projectsInError.nonEmpty
    }
  }

  override def invalidateAfterLoad: Boolean = true
  override def clean(implicit monitor: IProgressMonitor): Unit = buildScopeUnits.foreach { _.clean }
  override def canTrackDependencies: Boolean = true

  private def findProjectsInError(scopeUnit: BuildScopeUnit) = {
    def hasErrors(project: IProject, scope: CompileScope): Boolean = {
      IScalaPlugin().getScalaProject(project).buildManager match {
        case manager: SbtScopesBuildManager => manager.hasErrors(scope)
        case manager: EclipseBuildManager => manager.hasErrors
      }
    }
    for {
      scope <- scopeUnit.scope.dependentScopesInUpstreamProjects
      project <- owningProject.transitiveDependencies if hasErrors(project, scope)
    } yield project
  }

  private def isStopBuildOnErrors = {
    val stopBuildOnErrorsProperty = SettingConverterUtil.convertNameToProperty(ScalaPluginSettings.stopBuildOnErrors.name)
    owningProject.storage.getBoolean(stopBuildOnErrorsProperty)
  }

  private def putMarkersForTransitives(scopeWithError: ScopeUnitWithProjectsInError): Unit = {
    if (scopeWithError.projectsInError.nonEmpty) {
      val errorProjects = scopeWithError.projectsInError.map(_.getName).toSet.mkString(", ")
      val currentScopeName = scopeWithError.owner.scope.name
      BuildProblemMarker.create(owningProject.underlying,
        s"Project ${owningProject.underlying.getName} ${currentScopeName} not built due to errors in dependent project(s) $errorProjects")
    }
  }

  override def latestAnalysis(incOptions: => IncOptions): Analysis = Analysis.Empty

  override def buildManagerOf(outputFile: File): Option[EclipseBuildManager] =
    buildScopeUnits.find { _.buildManagerOf(outputFile).nonEmpty }
}

private case class ScopeUnitWithProjectsInError(owner: BuildScopeUnit, projectsInError: Seq[IProject])