package org.scalaide.core.internal.launching

import java.util.concurrent.atomic.AtomicBoolean

import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.SdtConstants

trait ProblemHandlersForLaunchDelegate extends AbstractJavaLaunchConfigurationDelegate {
  /** Scala problem markers should prevent a launch. This integrates with the platform and correctly displays a dialog. */
  override protected def isLaunchProblem(problemMarker: IMarker): Boolean =
    super.isLaunchProblem(problemMarker) || {
      val isError = Option(problemMarker.getAttribute(IMarker.SEVERITY)).map(_.asInstanceOf[Int] >= IMarker.SEVERITY_ERROR).getOrElse(false)
      isError && SdtConstants.ScalaErrorMarkerIds.contains(problemMarker.getType())
    }
}

trait MainClassFinalCheckForLaunchDelegate extends AbstractJavaLaunchConfigurationDelegate {
  val existsProblemsAccessor: IProject => Boolean
    /** Stop a launch if the main class does not exist. */
  override def finalLaunchCheck(configuration: ILaunchConfiguration, mode: String, monitor: IProgressMonitor): Boolean = {
    // verify that the main classfile exists
    val project = getJavaProject(configuration)
    val mainTypeName = getMainTypeName(configuration)
    IScalaPlugin().asScalaProject(project.getProject) map { scalaProject =>
      val mainClassVerifier = new MainClassVerifier
      val status = mainClassVerifier.execute(scalaProject, mainTypeName, existsProblemsAccessor(project.getProject))
      if (!status.isOK) {
        val prompter = DebugPlugin.getDefault().getStatusHandler(status)
        val continueLaunch = new AtomicBoolean(false)
        if (prompter != null)
          prompter.handleStatus(status, continueLaunch)
        continueLaunch.get()
      } else true
    } getOrElse false
  }
}
