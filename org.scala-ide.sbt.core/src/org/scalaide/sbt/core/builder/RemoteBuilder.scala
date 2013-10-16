package org.scalaide.sbt.core.builder

import java.util.{ Map => JMap }
import scala.tools.eclipse.logging.HasLogger
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.IProgressMonitor
import org.scalaide.sbt.core.SbtRemoteProcess

class RemoteBuilder extends IncrementalProjectBuilder with HasLogger {

  private def getSbtProcess() = {
    SbtRemoteProcess.getCachedProcessFor(getProject().getLocation().toFile())
  }

  override def build(kind: Int, args: JMap[String, String], monitor: IProgressMonitor): Array[IProject] = {
    val sbtProcess = getSbtProcess

    val compilationSuccess = sbtProcess.compile()

    println(s"Compilation result is: $compilationSuccess")

    val keys = sbtProcess.getSettingKeys("baseDirectory")

    println(s"keys:\n ${keys.mkString("\n")}")

    val keyValue = sbtProcess.getSettingValue(keys.head)

    println(s"key value: ${keyValue}")

    val taskKeys = sbtProcess.getTaskKeys("fullClasspath")

    println(s"tasks:\n ${taskKeys.mkString("\n")}")

    val taskValue = sbtProcess.getTaskValue(taskKeys.head)

    println(s"task value : ${taskValue}")

    // TODO: get the compilation result (errors, ...)
    // TODO: refresh the output folders

    if (compilationSuccess) {
      getProject.getReferencedProjects()
    } else {
      Array()
    }
  }

}