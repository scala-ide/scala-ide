package scala.tools.eclipse.launching

import org.eclipse.jdt.launching.{AbstractJavaLaunchConfigurationDelegate, JavaRuntime,
	                                IRuntimeClasspathEntry, VMRunnerConfiguration, ExecutionArguments}
import scala.tools.eclipse.ScalaPlugin
import java.io.File
import com.ibm.icu.text.MessageFormat
import org.eclipse.core.runtime.{Path, CoreException, IProgressMonitor, NullProgressMonitor}
import org.eclipse.debug.core.{ILaunch, ILaunchConfiguration}
import org.eclipse.jdt.internal.launching.LaunchingMessages
import scala.collection.JavaConversions._
import scala.collection.mutable
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants
import ScalaTestLaunchConstants._
import scala.tools.eclipse.javaelements.ScalaSourceFile
import org.eclipse.jface.dialogs.MessageDialog
import org.eclipse.core.resources.ResourcesPlugin

class ScalaTestLaunchDelegate extends AbstractJavaLaunchConfigurationDelegate {
  def launch(configuration: ILaunchConfiguration, mode: String, launch: ILaunch, monitor0: IProgressMonitor) {
		
		val monitor = if (monitor0 == null) new NullProgressMonitor() else monitor0
		
		monitor.beginTask(configuration.getName() + "...", 3)

		if (monitor.isCanceled())
			return
			
		try {
			monitor.subTask(LaunchingMessages.JavaLocalApplicationLaunchConfigurationDelegate_Verifying_launch_attributes____1) 
							
			val mainTypeName = "org.scalatest.tools.Runner"
			val runner = getVMRunner(configuration, mode)
	
			val workingDir = verifyWorkingDirectory(configuration)
		    val workingDirName = if (workingDir != null) workingDir.getAbsolutePath() else null
			
			// Environment variables
			val envp = getEnvironment(configuration)
			
			// Test Class
			val stArgs = getScalaTestArgs(configuration)
			
			// Program & VM arguments
			val pgmArgs = getProgramArguments(configuration) + " " + stArgs + " -oW -g"		
			val vmArgs = getVMArguments(configuration)
			val execArgs = new ExecutionArguments(vmArgs, pgmArgs)
			
			// VM-specific attributes
			val vmAttributesMap = getVMSpecificAttributesMap(configuration)

			val modifiedAttrMap: mutable.Map[String, Array[String]] =
				if (vmAttributesMap == null) mutable.Map() else vmAttributesMap.asInstanceOf[java.util.Map[String,Array[String]]]
			val classpath0 = getClasspath(configuration)
			val missingScalaLibraries = toInclude(modifiedAttrMap,
					classpath0.toList, configuration)
			// Classpath
			// Add scala libraries that were missed in VM attributes
			val classpath = (classpath0.toList):::missingScalaLibraries
			
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
			
			monitor.subTask(LaunchingMessages.JavaLocalApplicationLaunchConfigurationDelegate_Creating_source_locator____2) 
			// set the default source locator if required
			setDefaultSourceLocator(launch, configuration)
			monitor.worked(1)
			
			// Launch the configuration - 1 unit of work
			runner.run(runConfig, launch, monitor)
			
			// check for cancellation
			if (monitor.isCanceled())
				return
		}
		finally {
			monitor.done()
		}
	}
	
	private def toInclude(vmMap: mutable.Map[String, Array[String]], classpath: List[String],
			          configuration: ILaunchConfiguration): List[String] =
		missingScalaLibraries((vmMap.values.flatten.toList) ::: classpath, configuration)

  private def missingScalaLibraries(included: List[String], configuration: ILaunchConfiguration): List[String] =  {
		val entries = JavaRuntime.computeUnresolvedRuntimeClasspath(configuration).toList
		val libid = Path.fromPortableString(ScalaPlugin.plugin.scalaLibId)
		val found = entries.find(e => e.getClasspathEntry != null && e.getClasspathEntry.getPath == libid)
		found match {
			case Some(e) =>
			  val scalaLibs = resolveClasspath(e, configuration)
			  scalaLibs.diff(included)
			case None =>
			  List()
		}
	}
	
  private def resolveClasspath(a: IRuntimeClasspathEntry, configuration: ILaunchConfiguration): List[String] = {
    val bootEntry = JavaRuntime.resolveRuntimeClasspath(Array(a), configuration)
    bootEntry.toList.map(_.getLocation())
  }
  
  private def getScalaTestArgs(configuration: ILaunchConfiguration): String = {
    val launchType = configuration.getAttribute(SCALATEST_LAUNCH_TYPE_NAME, TYPE_SUITE)
    launchType match {
      case TYPE_SUITE => 
        val suiteClass = configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, "")
        if (suiteClass.length > 0) "-s " + suiteClass else ""
      case TYPE_FILE =>
        val filePortablePath = configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, "")
        if (filePortablePath.length > 0) {
          val scSrcFileOpt = ScalaSourceFile.createFromPath(filePortablePath)
          scSrcFileOpt match {
            case Some(scSrcFile) => 
              scSrcFile.getTypes
                .filter(ScalaTestLaunchShortcut.isScalaTestSuite(_))
                .map(iType => "-s " + iType.getFullyQualifiedName)
                .mkString(" ")
            case None => 
              MessageDialog.openError(null, "Error", "File '" + filePortablePath + "' not found.")
              ""
          }
        }
        else
          ""
      case TYPE_PACKAGE =>
        val packageName = configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, "")
        val workspace = ResourcesPlugin.getWorkspace()
        val outputDir = new File(workspace.getRoot.getLocation.toFile, JavaRuntime.getProjectOutputDirectory(configuration)).getAbsolutePath
        if (packageName.length > 0) {
          val includeNested = configuration.getAttribute(SCALATEST_LAUNCH_INCLUDE_NESTED_NAME, INCLUDE_NESTED_FALSE)
          if (includeNested == INCLUDE_NESTED_TRUE) 
            "-p \"" + outputDir + "\" -w " + packageName
          else
            "-p \"" + outputDir + "\" -m " + packageName
        }
        else
          ""
      case _ =>
        ""
    }
  }
}