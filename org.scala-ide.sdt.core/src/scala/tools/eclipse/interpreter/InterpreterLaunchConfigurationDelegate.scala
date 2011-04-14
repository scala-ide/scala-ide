/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.interpreter

import java.io.File

import org.eclipse.debug.core.model.IProcess
import org.eclipse.core.runtime.{ IProgressMonitor, NullProgressMonitor }
import org.eclipse.debug.core.{ ILaunch, ILaunchConfiguration, ILaunchListener, IDebugEventSetListener, DebugEvent }
import org.eclipse.jdt.launching.{ AbstractJavaLaunchConfigurationDelegate, ExecutionArguments, VMRunnerConfiguration }
import org.eclipse.ui.console.ConsolePlugin
import org.eclipse.debug.ui.console.ConsoleColorProvider
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.debug.ui.IDebugUIConstants

import scala.tools.eclipse.ScalaPlugin

/**
 * This launch delegate extends the normal JavaLaunchDelegate with functionality to work for the interpreter.
 */
class InterpreterLaunchConfigurationDelegate extends 
	AbstractJavaLaunchConfigurationDelegate with ILaunchListener {  
  
  override def launch(configuration : ILaunchConfiguration, mode : String, launch : ILaunch, monitor : IProgressMonitor) {
    val mon : IProgressMonitor = if(monitor == null) new NullProgressMonitor() else monitor
    
    def reconfigureLaunchListeners() {
   	 val manager = DebugPlugin.getDefault.getLaunchManager 
//   	 println("launch is currently registered: " + manager.isRegistered(launch))
   	 manager.removeLaunch(launch)
//   	 manager.addLaunchListener(this)
   	 DebugPlugin.getDefault.addDebugEventListener(this)
    }
    
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
   	reconfigureLaunchListeners()
      doTheLaunch()
    } finally {
      mon.done();
    }
  }

  override def launchAdded(launch: ILaunch) {  
	  launchChanged(launch)
  }
  
  override def launchRemoved(launch: ILaunch) {  }
  
  override def launchChanged(launch: ILaunch) {
	 println("Launchy changed! " + launch.getLaunchMode)
	 val processes = launch.getProcesses
	 processes.foreach { p =>
		 getReplConsole(p) match {
			 case Some(c) if c.getDocument != null => // console already allocated, do nothing
			 case _ =>
			 	// TODO: activate the console.
			 	val colorProvider = new ConsoleColorProvider() 
			 	val encoding = launch.getAttribute(DebugPlugin.ATTR_CONSOLE_ENCODING)
			 	val replConsole = new ReplConsole("New Scala Repl!!", p, colorProvider)
            replConsole.setAttribute(IDebugUIConstants.ATTR_CONSOLE_PROCESS, p);
			 	getConsoleManager.addConsoles(Array(replConsole))
			 	getConsoleManager.showConsoleView(replConsole)
		 }
	 }
  }
  
  def getReplConsole(process: IProcess): Option[ReplConsole] = {
	  val consoles = getConsoleManager.getConsoles
	  val matching = consoles.collect { case con: ReplConsole if con.process == process => con }
	  matching.headOption
  }
  
  /** Retreives the extra classpath needed for the interpreter*/
  def toolClassPath = {
    val plugin = ScalaPlugin.plugin
    import plugin._
    (libClasses :: dbcClasses :: swingClasses :: compilerClasses :: Nil).flatMap(_.toList).map(_.toOSString)
  }
  
	/** helper method to get the console manager of the eclipse console plugin */
	def getConsoleManager = ConsolePlugin.getDefault.getConsoleManager
	
	override def handleDebugEvents(events: Array[DebugEvent]) {
		super.handleDebugEvents(events)
		println("Handling debug events!!! " + events.map(e => e.getSource.getClass).toList)
		for (e <- events) {
			if (e.getKind == DebugEvent.CREATE) {
				e.getSource match {
					case process: IProcess => launchChanged(process.getLaunch)
					case _ =>
				}
			} else if (e.getKind == DebugEvent.TERMINATE) {
				DebugPlugin.getDefault.removeDebugEventListener(this) // FIXME: don't want to remove for all launches, just the ones we were supposed to listen to
				// TODO: remove the console!
			}
		}
	}
}
