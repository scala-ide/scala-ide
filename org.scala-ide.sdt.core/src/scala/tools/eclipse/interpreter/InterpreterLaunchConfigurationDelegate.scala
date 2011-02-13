/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.interpreter

import java.io.File

import org.eclipse.core.runtime.{ IProgressMonitor, NullProgressMonitor }
import org.eclipse.debug.core.{ ILaunch, ILaunchConfiguration }
import org.eclipse.jdt.launching.{ AbstractJavaLaunchConfigurationDelegate, ExecutionArguments, VMRunnerConfiguration }

import scala.tools.eclipse.ScalaPlugin

/**
 * This launch delegate extends the normal JavaLaunchDelegate with functionality to work for the interpreter.
 */
class InterpreterLaunchConfigurationDelegate extends AbstractJavaLaunchConfigurationDelegate {  
  
  override def launch(configuration : ILaunchConfiguration, mode : String, launch : ILaunch, monitor : IProgressMonitor) {
    val mon : IProgressMonitor = if(monitor == null) new NullProgressMonitor() else monitor
    //Helper method to actually perform the launch inside a try-catch block.
    def doTheLaunch() {        
      val mainClass = "scala.tools.nsc.MainInterpreter"
      
      mon.beginTask(configuration.getName(), 3); //$NON-NLS-1$
      //We need lots of early outs if possible...
      if(mon.isCanceled) {
        return;
      }
      
      val vmRunner = getVMRunner(configuration, mode)
      //TODO - Check for null/existence of working directory... (should this always be defined?)
      val workingDir = { 
        val dir = getWorkingDirectory(configuration)
        if(dir != null) {
          dir.getAbsolutePath
        } else {
          null
        }
      }

      val envp = getEnvironment(configuration)
      
      val vmAttrs = getVMSpecificAttributesMap(configuration)
      val execArgs = new ExecutionArguments(getVMArguments(configuration), getProgramArguments(configuration))    
      val vmArgs = execArgs.getVMArgumentsArray()
      val classpath = (toolClassPath ++ getClasspath(configuration)).mkString(File.pathSeparator)
      val programArgs = Array("-Xnojline", "-classpath", classpath) ++ execArgs.getProgramArgumentsArray() 
      
      val runConfig = new VMRunnerConfiguration(mainClass, toolClassPath.toArray);
      runConfig.setWorkingDirectory(workingDir);
      runConfig.setEnvironment(envp);
      runConfig.setVMArguments(vmArgs);
      runConfig.setVMSpecificAttributesMap(vmAttrs);
      runConfig.setProgramArguments(programArgs);
    
      // Bootpath - TODO - Add scala library/compiler here
      runConfig.setBootClassPath(getBootpath(configuration));
      
      // check for cancellation (again)
      if (mon.isCanceled()) {
        return;
      }  
      // done the verification phase
      mon.worked(1);
   
      // set the default source locator if required
      setDefaultSourceLocator(launch, configuration);
      mon.worked(1);    
    
      // Launch the configuration
      vmRunner.run(runConfig, launch, monitor);
    
      //send extra commands to the configuration
      if(mon.isCanceled) {
        return;
      }
      runSeedscripts()
        
      // check for cancellation
      if (mon.isCanceled()) {
        return;
      }     
    }
    /** Helper method to deal with launch configuration */
    implicit def pimpConfiguration(configuration : ILaunchConfiguration) = new {
      def getAttributeOption(name : String) : Option[String] = {
        configuration.getAttribute(name, "") match {
          case null => None
          case "" => None
          case x => Some(x)
        }
      }
    }
    /** Seeds the interpreter with imports */
    def runSeedscripts() {
      import InterpreterLaunchConstants._
      
      def seedInterpreter(namespace : Option[String], asNamespace : Boolean) {
       for {pkg <- namespace
           process <- launch.getProcesses
           val streamProxy = process.getStreamsProxy
           if streamProxy != null
       } {
         //TODO - Don't just write, flush!
         if(asNamespace) {
           streamProxy.write("import " + pkg + "._")
         } else {
           streamProxy.write(pkg)
         }
         //TODO - Is this needed?
         
         streamProxy.write("\r\n")
       }
      }
      seedInterpreter(configuration.getAttributeOption(SEED_SCRIPT), false)
      seedInterpreter(configuration.getAttributeOption(PACKAGE_IMPORT), true)
      seedInterpreter(configuration.getAttributeOption(OBJECT_IMPORT), true)
    }
    
    try {
      doTheLaunch()
    } finally {
      mon.done();
    }
  }
  
  /** Retreives the extra classpath needed for the interpreter*/
  def toolClassPath = {
    val plugin = ScalaPlugin.plugin
    import plugin._
    (libClasses :: dbcClasses :: swingClasses :: compilerClasses :: Nil).flatMap(_.toList).map(_.toOSString)
  }
}
