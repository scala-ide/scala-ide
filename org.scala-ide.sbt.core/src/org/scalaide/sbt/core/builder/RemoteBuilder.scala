package org.scalaide.sbt.core.builder

import java.io.File

import scala.concurrent.Await
import scala.concurrent.duration._

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.SubMonitor
import org.eclipse.jdt.core.IJavaModelMarker
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.IScalaProject
import org.scalaide.core.SdtConstants
import org.scalaide.core.internal.builder.BuildProblemMarker
import org.scalaide.core.internal.builder.EclipseBuildManager
import org.scalaide.logging.HasLogger
import org.scalaide.sbt.core.SbtBuild
import org.scalaide.sbt.core.SbtRemotePlugin
import org.scalaide.ui.internal.preferences.ScalaPluginSettings
import org.scalaide.util.internal.SettingConverterUtil

trait Shutdownable {
  def shutdown(): Unit
}

class RemoteBuilder(project: IScalaProject) extends EclipseBuildManager with Shutdownable with HasLogger {
  private lazy val buildReporter = new RemoteBuildReporter(project)

  override def build(ingnoreAddedOrUpdated: Set[IFile], ignoreRemoved: Set[IFile], monitor: SubMonitor): Unit = {
    implicit val system = SbtRemotePlugin.system
    import system.dispatcher

    project.underlying.deleteMarkers(SdtConstants.ProblemMarkerId, true, IResource.DEPTH_INFINITE)
    val dependentProjectsWithErrors = findProjectsInError
    if (dependentProjectsWithErrors.isEmpty || shouldBuildContinueOnErrors) {
      val build = SbtBuild.buildFor(project.underlying.getLocation().toFile())
      val res = build.flatMap { sbtBuild =>
        sbtBuild.compileWithResult(project, buildReporter)
      }
      val compilationResult = Await.result(res, Duration.Inf)
      compilationResult.foreach { compilationResult =>
        logger.debug(s"build of project ${project.underlying.getName} with $compilationResult")
      }
    } else
      putMarkersForTransitives(dependentProjectsWithErrors)
  }

  def invalidateAfterLoad: Boolean = false

  def clean(implicit monitor: IProgressMonitor): Unit = {}

  def shutdown(): Unit = {
    implicit val system = SbtRemotePlugin.system
    import system.dispatcher
    Await.result(SbtBuild.shutdown(), Duration.Inf)
  }

  def canTrackDependencies: Boolean = false

  def buildManagerOf(outputFile: File): Option[EclipseBuildManager] = Some(this)

  private def foundJavaMarkers = project.underlying.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE).toSet
  private def foundScalaMarkers = project.underlying.findMarkers(SdtConstants.ProblemMarkerId, true, IResource.DEPTH_INFINITE)
  override def buildErrors: Set[IMarker] = foundJavaMarkers ++ foundScalaMarkers
  override def hasErrors: Boolean = buildErrors.nonEmpty

  private def findProjectsInError = {
    def hasErrors(thatProject: IProject): Boolean =
      IScalaPlugin().asScalaProject(thatProject).map {
        _.buildManager.hasErrors
      }.getOrElse(false)
    for {
      thatProject <- project.transitiveDependencies if hasErrors(thatProject)
    } yield thatProject
  }

  private def shouldBuildContinueOnErrors = {
    val stopBuildOnErrorsProperty = SettingConverterUtil.convertNameToProperty(ScalaPluginSettings.stopBuildOnErrors.name)
    !project.storage.getBoolean(stopBuildOnErrorsProperty)
  }

  private def putMarkersForTransitives(projectsInError: Seq[IProject]): Unit = {
    if (projectsInError.nonEmpty) {
      val errorProjects = projectsInError.map(_.getName).toSet.mkString(", ")
      val rootErrors = projectsInError.flatMap { project =>
        val foundErrors = IScalaPlugin().asScalaProject(project).toList.flatMap {
          _.buildManager.buildErrors
        }
        foundErrors
      }.toSet[IMarker].map {
        _.getAttribute(IMarker.MESSAGE, "No message")
      }.mkString(";")
      BuildProblemMarker.create(project.underlying,
        s"""Project: "${project.underlying.getName}" not build due to errors""" +
          s""" in dependent project(s): $errorProjects. Root error(s): $rootErrors""")
    }
  }
}
