package org.scalaide.sbt.core.builder

import java.io.File

import scala.concurrent.Await
import scala.concurrent.duration._

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.SubMonitor
import org.scalaide.core.IScalaProject
import org.scalaide.core.internal.builder.EclipseBuildManager
import org.scalaide.logging.HasLogger
import org.scalaide.sbt.core.SbtBuild
import org.scalaide.sbt.core.SbtRemotePlugin

trait Shutdownable {
  def shutdown(): Unit
}

class RemoteBuilder(project: IScalaProject) extends EclipseBuildManager with Shutdownable with HasLogger {
  private lazy val buildReporter = new RemoteBuildReporter(project)

  override def build(ingnoreAddedOrUpdated: Set[IFile], ignoreRemoved: Set[IFile], monitor: SubMonitor): Unit = {
    implicit val system = SbtRemotePlugin.system
    import system.dispatcher

    val build = SbtBuild.buildFor(project.underlying.getLocation().toFile())
    val res = build.flatMap { sbtBuild =>
      sbtBuild.compile(project.underlying).flatMap { compilationId =>
        sbtBuild.compilationResult(compilationId, buildReporter)
      }
    }
    val id = Await.result(res, Duration.Inf)

    logger.debug(s"build of project ${project.underlying.getName} with remote builder triggered (id: $id)")
  }

  def invalidateAfterLoad: Boolean = false

  def clean(implicit monitor: IProgressMonitor): Unit = {}

  def shutdown(): Unit = {
    implicit val system = SbtRemotePlugin.system
    import system.dispatcher
    Await.result(SbtBuild.shutdown, Duration.Inf)
  }

  def canTrackDependencies: Boolean = false

  def buildManagerOf(outputFile: File): Option[EclipseBuildManager] = Some(this)

  def buildErrors: Set[IMarker] = ???
}
