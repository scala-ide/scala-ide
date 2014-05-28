package org.scalaide.core.internal.launching

import scala.collection.mutable
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate
import org.eclipse.jdt.launching.JavaRuntime
import org.eclipse.jdt.launching.IRuntimeClasspathEntry
import org.eclipse.jdt.launching.VMRunnerConfiguration
import org.eclipse.jdt.launching.ExecutionArguments
import org.scalaide.core.ScalaPlugin
import com.ibm.icu.text.MessageFormat
import org.eclipse.core.runtime.Path
import org.eclipse.core.runtime.CoreException
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.debug.core.ILaunch
import org.eclipse.debug.core.ILaunchConfiguration
import org.scalaide.util.internal.ui.DisplayThread
import org.eclipse.core.resources.IMarker
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.IStatus
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.Path
import org.eclipse.debug.core.ILaunch
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.jdt.internal.launching.LaunchingMessages
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate
import org.eclipse.jdt.launching.ExecutionArguments
import org.eclipse.jdt.launching.IRuntimeClasspathEntry
import org.eclipse.jdt.launching.JavaRuntime
import org.eclipse.jdt.launching.VMRunnerConfiguration
import org.eclipse.jface.dialogs.MessageDialog
import scala.collection.JavaConverters
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.core.runtime.Status
import org.eclipse.debug.internal.core.IInternalDebugCoreConstants
import org.eclipse.jface.dialogs.MessageDialogWithToggle
import org.eclipse.jface.dialogs.IDialogConstants
import java.util.concurrent.atomic.AtomicBoolean

class ScalaLaunchDelegate extends AbstractJavaLaunchConfigurationDelegate {
  /** This code is very heavily inspired from `JavaLaunchDelegate`. */
  def launch(configuration: ILaunchConfiguration, mode: String, launch: ILaunch, monitor0: IProgressMonitor) {

    val monitor = if (monitor0 == null) new NullProgressMonitor() else monitor0

    monitor.beginTask(configuration.getName() + "...", 3)

    if (monitor.isCanceled())
      return

    try {
      monitor.subTask(LaunchingMessages.JavaLocalApplicationLaunchConfigurationDelegate_Verifying_launch_attributes____1)

      val mainTypeName = verifyMainTypeName(configuration)
      val runner = getVMRunner(configuration, mode)

      val workingDir = verifyWorkingDirectory(configuration)
      val workingDirName = if (workingDir != null) workingDir.getAbsolutePath() else null

      // Environment variables
      val envp = getEnvironment(configuration)

      // Program & VM arguments
      val pgmArgs = getProgramArguments(configuration)
      val vmArgs = getVMArguments(configuration)
      val execArgs = new ExecutionArguments(vmArgs, pgmArgs)

      // VM-specific attributes
      val vmAttributesMap = getVMSpecificAttributesMap(configuration)

      import JavaConverters._

      // adding Scala libraries is the only difference compared to the Java Launcher
      // TODO: do we still need this?
      val modifiedAttrMap: mutable.Map[String, Array[String]] =
        if (vmAttributesMap == null) mutable.Map()
        else vmAttributesMap.asInstanceOf[java.util.Map[String, Array[String]]].asScala
      val classpath0 = getClasspath(configuration)
      val missingScalaLibraries = toInclude(modifiedAttrMap,
        classpath0.toList, configuration)
      // Classpath
      // Add scala libraries that were missed in VM attributes
      val classpath = (classpath0.toList) ::: missingScalaLibraries

      // Create VM config
      val runConfig = new VMRunnerConfiguration(mainTypeName, classpath.toArray)
      runConfig.setProgramArguments(execArgs.getProgramArgumentsArray())
      runConfig.setEnvironment(envp)
      runConfig.setVMArguments(execArgs.getVMArgumentsArray())
      runConfig.setWorkingDirectory(workingDirName)
      runConfig.setVMSpecificAttributesMap(vmAttributesMap)

      // Bootpath
      runConfig.setBootClassPath(getBootpath(configuration))

      // check for cancellation
      if (monitor.isCanceled())
        return

      // stop in main
      prepareStopInMain(configuration)

      // done the verification phase
      monitor.worked(1)

      // check for cancellation
      if (monitor.isCanceled()) return
      monitor.subTask(LaunchingMessages.JavaLocalApplicationLaunchConfigurationDelegate_Creating_source_locator____2)
      // set the default source locator if required
      setDefaultSourceLocator(launch, configuration)
      monitor.worked(1)

      // Launch the configuration - 1 unit of work
      runner.run(runConfig, launch, monitor)

      // check for cancellation
      if (monitor.isCanceled())
        return
    } finally {
      monitor.done()
    }
  }

  /** Scala problem markers should prevent a launch. This integrates with the platform and correctly displays a dialog. */
  override protected def isLaunchProblem(problemMarker: IMarker): Boolean =
    super.isLaunchProblem(problemMarker) || {
      val isError = Option(problemMarker.getAttribute(IMarker.SEVERITY)).map(_.asInstanceOf[Integer].intValue >= IMarker.SEVERITY_ERROR).getOrElse(false)
      isError && ScalaPlugin.plugin.scalaErrorMarkers.contains(problemMarker.getType())
    }

  /** Stop a launch if the main class does not exist. */
  override def finalLaunchCheck(configuration: ILaunchConfiguration, mode: String, monitor: IProgressMonitor): Boolean = {
    // verify that the main classfile exists
    val project = getJavaProject(configuration)
    val mainTypeName = getMainTypeName(configuration)
    ScalaPlugin.plugin.asScalaProject(project.getProject) map { scalaProject =>
      val mainClassVerifier = new MainClassVerifier
      val status = mainClassVerifier.execute(scalaProject, mainTypeName, existsProblems(project.getProject))
      if (!status.isOK) {
        val prompter = DebugPlugin.getDefault().getStatusHandler(status)
        val continueLaunch = new AtomicBoolean(false)
        if (prompter != null)
          prompter.handleStatus(status, continueLaunch)
        continueLaunch.get()
      } else true
    } getOrElse false
  }

  private def toInclude(vmMap: mutable.Map[String, Array[String]], classpath: List[String],
    configuration: ILaunchConfiguration): List[String] =
    missingScalaLibraries((vmMap.values.flatten.toList) ::: classpath, configuration)

  private def missingScalaLibraries(included: List[String], configuration: ILaunchConfiguration): List[String] = {
    val entries = JavaRuntime.computeUnresolvedRuntimeClasspath(configuration).toList
    val libid = Path.fromPortableString(ScalaPlugin.plugin.scalaLibId)
    val found = entries.find(e => e.getClasspathEntry != null && e.getClasspathEntry.getPath == libid)
    found match {
      case Some(e) =>
        val scalaLibs = resolveClasspath(e, configuration)
        scalaLibs.diff(included)
      case None =>
        Nil
    }
  }

  private def resolveClasspath(a: IRuntimeClasspathEntry, configuration: ILaunchConfiguration): List[String] = {
    val bootEntry = JavaRuntime.resolveRuntimeClasspath(Array(a), configuration)
    bootEntry.toList.map(_.getLocation())
  }
}
