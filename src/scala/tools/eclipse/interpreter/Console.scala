/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 * @author Josh Suereth
 */
// $Id$

package scala.tools.eclipse.interpreter

import java.io.{ File, InputStream, IOException, OutputStream, PrintWriter }
import java.net.URL

import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.{ FileLocator, Platform }
import org.eclipse.jdt.core.{ IClasspathEntry, IJavaElement, IJavaProject, IPackageFragment }
import org.eclipse.jdt.launching.JavaRuntime
import org.eclipse.jface.resource.ImageDescriptor
import org.eclipse.osgi.baseadaptor.BaseData
import org.eclipse.osgi.baseadaptor.bundlefile.BundleFile
import org.eclipse.osgi.framework.internal.core.AbstractBundle
import org.eclipse.swt.widgets.Display
import org.eclipse.ui.console.{ IOConsole, ConsolePlugin }
import org.osgi.framework.Bundle

/**
 * The object manages ALL interpreters for Scala within eclipse.
 */
object ScalaConsoleMgr {
  var consoles  = new scala.collection.mutable.LinkedHashMap[IJavaElement,Console]
  
  def imageDesc = {
    ImageDescriptor.createFromURL(new URL(ScalaPlugin.plugin.getBundle.getEntry("/icons/full/"), "etool16/scala_app.gif"))
  }
   
  def nameFor(project : ScalaPlugin#Project, pkg:String) = {
    var str = "Scala interpreter in "
    if (pkg != null) {
      str += "package " + pkg + " of "
    }
    str + project.underlying.getName
  }
   
  def removeConsole(console : Console) = {
    consoles.retain( (key : IJavaElement, obj : Console) => !obj.equals(console))
  }
   
  def mkConsole(element : IJavaElement) = {
    try {
      var console = consoles.get(element)
      if (console == None) {
        var project : IProject = null
        var pkg : String =""
        if (element.isInstanceOf[IJavaProject]) {
          project = element.asInstanceOf[IJavaProject].getProject
          pkg = null
        } else {
          val pkgFrag = element.asInstanceOf[IPackageFragment]
          project = pkgFrag.getResource.getProject
          pkg = pkgFrag.getElementName
        }
                        
        console = Some(new Console(element, ScalaPlugin.plugin.projectSafe(project).get, pkg))
        consoles.put(element, console.get)
      }
      
      ConsolePlugin.getDefault.getConsoleManager.showConsoleView(console.get)
    } catch {
      case t: Throwable => t.printStackTrace
    }
  }
}

/**
 * Helper methods for creating an interpreter.
 */
trait InterpreterHelper {
  
  val mainClass = "scala.tools.nsc.MainInterpreter"
  /**
   * Retreives the vm location for a given project.
   */
  def getJvmPath(project : ScalaPlugin#Project) = {
    //Pull VM for this project
    var vm = JavaRuntime.getVMInstall(project.javaProject)
    new File(vm.getInstallLocation, "bin/java")
  }
  
  /**
   * Creates the correct classpath for the given project and optional package.
   * @project The project the interpeter is "on"
   * @pkg  The name of an initial package to import or null if no default package is imported.
   */
  def makeClassPath(project : ScalaPlugin#Project, pkg : String) : String = {
    var toReturn = project.outputPath
    try {
      var cps = project.javaProject.getResolvedClasspath(true)
      cps filter { _.getEntryKind != IClasspathEntry.CPE_SOURCE } foreach {
        tmp => toReturn +=(File.pathSeparator + tmp.getPath.makeAbsolute.toOSString)
      }
    } catch {
      case e :Throwable => {
        ScalaPlugin.plugin.logError("Problem making classpath for interpreter", e)
        throw new Error
      }
    } 
   
    def addBundleEntry(classpath : String, bundle: Bundle, path : String) = {
      val entryURL = bundle.getEntry(path)
      if(entryURL == null)
        classpath
      else {
        val bundlePath = FileLocator.resolve(entryURL).getPath
        if(bundlePath.endsWith("!/"))
          classpath + File.pathSeparator + bundlePath.subSequence(0, bundlePath.length-2).toString
        else
          classpath + File.pathSeparator + bundlePath
      }
    }
    
    val compilerBundle = Platform.getBundle("scala.tools.nsc")

    // The compiler if scala.tools.nsc is unexploded
    toReturn = addBundleEntry(toReturn, compilerBundle, "/")
    // The compiler if scala.tools.nsc is exploded in an outer workspace
    toReturn = addBundleEntry(toReturn, compilerBundle, "/bin")
    toReturn = addBundleEntry(toReturn, compilerBundle, "/lib/fjbg.jar")

    def addEmbeddedBundleEntry(classpath : String, bf : BundleFile, path : String) = {
      val entryFile = bf.getFile(path, false)
      if(entryFile != null)
        classpath + File.pathSeparator + entryFile.getAbsolutePath
      else
        classpath
    }
    
    // Some projects (notably scala, scala-library and scala-plugin) don't include
    // the library on their classpath, so we add it explicitly here.
    // Nb. this depends on Eclipse internals and should be replaced with something
    // more stable if the opportunity arises.
    val libraryBundleFile = 
      Platform.getBundle("scala.library").asInstanceOf[AbstractBundle].
        getBundleData.asInstanceOf[BaseData].getBundleFile
    
    toReturn += addEmbeddedBundleEntry(toReturn, libraryBundleFile, "/lib/scala-library.jar")
    toReturn += addEmbeddedBundleEntry(toReturn, libraryBundleFile, "/lib/scala-dbc.jar")
    toReturn += addEmbeddedBundleEntry(toReturn, libraryBundleFile, "/lib/scala-swing.jar")
    
    toReturn
  }
}

/**
 * This class represents a Scala Interpreter Console within the Eclipse application.
 */
class Console(selection : IJavaElement, project : ScalaPlugin#Project, pkg : String) 
  extends IOConsole(ScalaConsoleMgr.nameFor(project,pkg), ScalaConsoleMgr.imageDesc)
  with InterpreterHelper {
   
  var isClosed = false         

  ConsolePlugin.getDefault.getConsoleManager.addConsoles(Array(this))
  
  val command = getJvmPath(project)        
  val classpath = makeClassPath(project,pkg)
  
  var pb =
    new ProcessBuilder(
      command.getAbsolutePath,
      "-classpath",classpath,
      mainClass,
      "-Xnojline")
  pb.redirectErrorStream(true)
  
  //Create our variables
  var process = pb.start        

  //Import the package if needed
  if(pkg != null) {
    var tmp = new PrintWriter(process.getOutputStream)
    tmp.println("import " + pkg + "._")
    tmp.flush
  }

  new Spooler(getInputStream, process.getOutputStream).start
  new Spooler(process.getInputStream, newOutputStream).start
  
  class Spooler(in : InputStream, out : OutputStream) extends Thread {
    override def run = {
      try {
        val buffer = new Array[Byte](1024)
        var numRead = 0
        while({ numRead = in.read(buffer, 0, buffer.length) ; numRead } > 0) {
          out.write(buffer, 0, numRead)
          out.flush
        }
      } catch {
        case _ : IOException => // Deliberately ignored
      } finally {
        Display.getDefault.asyncExec(new Runnable { override def run = close })
      }
    }    
  }
  
  override def dispose = {
    close
    super.dispose
  }
  
  /**      
   * Closes the console and terminates its process
   */
  def close() {
    if(!isClosed) {
      isClosed = true
      ConsolePlugin.getDefault.getConsoleManager.removeConsoles(Array(this))
      ScalaConsoleMgr.removeConsole(this)
      
      // Termination of the process will cause the two spooler threads to terminate
      // if they haven't already done so
      process.destroy
    }
  }
  
  def refresh : Unit = {
    clearConsole
  }

  def getElement = selection  
}
