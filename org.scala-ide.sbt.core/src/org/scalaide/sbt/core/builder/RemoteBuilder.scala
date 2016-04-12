package org.scalaide.sbt.core.builder

import java.util.{Map => JMap}

import scala.concurrent.Await
import scala.concurrent.duration._

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.IProgressMonitor
import org.scalaide.logging.HasLogger
import org.scalaide.sbt.core.SbtBuild
import org.scalaide.sbt.core.SbtRemotePlugin

class RemoteBuilder extends IncrementalProjectBuilder with HasLogger {

  override def build(kind: Int, args: JMap[String, String], monitor: IProgressMonitor): Array[IProject] = {
    implicit val system = SbtRemotePlugin.system
    import system.dispatcher

    val project = getProject()
    val build = SbtBuild.buildFor(project.getLocation().toFile())
    val res = build.flatMap { _.compile(project) }
    val id = Await.result(res, Duration.Inf)

    logger.debug(s"build of project ${getProject.getName} with remote builder triggered (id: $id)")

    getProject.getReferencedProjects

    // TODO: get the compilation result (errors, ...)
    // TODO: refresh the output folders

  }

}
