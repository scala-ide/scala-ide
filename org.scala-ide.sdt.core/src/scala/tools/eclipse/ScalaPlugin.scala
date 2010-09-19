/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.jdt.core.IJavaProject
import scala.collection.mutable.HashMap
import scala.util.control.ControlThrowable

import org.eclipse.core.resources.{ IFile, IProject, IResourceChangeEvent, IResourceChangeListener, ResourcesPlugin }
import org.eclipse.core.runtime.{ CoreException, FileLocator, IStatus, Platform, Status }
import org.eclipse.core.runtime.content.IContentTypeSettings
import org.eclipse.jdt.core.{ ElementChangedEvent, IElementChangedListener, JavaCore, IJavaElementDelta }
import org.eclipse.jdt.internal.core.{ JavaModel, JavaProject, PackageFragment, PackageFragmentRoot }
import org.eclipse.jdt.internal.core.util.Util
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.swt.graphics.Color
import org.eclipse.ui.{ IEditorInput, IFileEditorInput, PlatformUI }
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext

import scala.tools.eclipse.javaelements.{ ScalaElement, ScalaSourceFile }
import scala.tools.eclipse.util.OSGiUtils.pathInBundle 
import scala.tools.eclipse.templates.ScalaTemplateManager

object ScalaPlugin { 
  var plugin : ScalaPlugin = _
}

class ScalaPlugin extends AbstractUIPlugin with IResourceChangeListener with IElementChangedListener {
  ScalaPlugin.plugin = this
  
  def pluginId = "org.scala-ide.sdt.core"
  def compilerPluginId = "org.scala-ide.scala.compiler"
  def libraryPluginId = "org.scala-ide.scala.library"
    
  def wizardPath = pluginId + ".wizards"
  def wizardId(name : String) = wizardPath + ".new" + name
  def classWizId = wizardId("Class")
  def traitWizId = wizardId("Trait")
  def objectWizId = wizardId("Object")
  def applicationWizId = wizardId("Application")
  def projectWizId = wizardId("Project")
  def netProjectWizId = wizardId("NetProject")
  
  def editorId = "scala.tools.eclipse.ScalaSourceFileEditor"
  def builderId = pluginId + ".scalabuilder"
  def natureId = pluginId + ".scalanature"  
  def launchId = "org.scala-ide.sdt.launching"
  val scalaCompiler = "SCALA_COMPILER_CONTAINER"
  val scalaLib = "SCALA_CONTAINER"
  def scalaCompilerId  = launchId + "." + scalaCompiler
  def scalaLibId  = launchId + "." + scalaLib
  def launchTypeId = "scala.application"
  def problemMarkerId = pluginId + ".problem"
  
  // Retained for backwards compatibility
  val oldPluginId = "ch.epfl.lamp.sdt.core"
  val oldLibraryPluginId = "scala.library"
  val oldNatureId = oldPluginId + ".scalanature"
  val oldBuilderId = oldPluginId + ".scalabuilder"
  val oldLaunchId = "ch.epfl.lamp.sdt.launching"
  val oldScalaLibId  = oldLaunchId + "." + scalaLib
  
  val scalaFileExtn = ".scala"
  val javaFileExtn = ".java"
  val jarFileExtn = ".jar"
  
  val scalaCompilerBundle = Platform.getBundle(ScalaPlugin.plugin.compilerPluginId)
  val compilerClasses = pathInBundle(scalaCompilerBundle, "/lib/scala-compiler.jar")
  val continuationsClasses = pathInBundle(scalaCompilerBundle, "/lib/continuations.jar")
  val compilerSources = pathInBundle(scalaCompilerBundle, "/lib/scala-compiler-src.jar")
  
  val scalaLibBundle = Platform.getBundle(ScalaPlugin.plugin.libraryPluginId)
  val libClasses = pathInBundle(scalaLibBundle, "/lib/scala-library.jar") 
  val libSources = pathInBundle(scalaLibBundle, "/lib/scala-library-src.jar") 
  val dbcClasses = pathInBundle(scalaLibBundle, "/lib/scala-dbc.jar")
  val dbcSources = pathInBundle(scalaLibBundle, "/lib/scala-dbc-src.jar") 
  val swingClasses = pathInBundle(scalaLibBundle, "/lib/scala-swing.jar") 
  val swingSources = pathInBundle(scalaLibBundle, "/lib/scala-swing-src.jar") 

  lazy val templateManager = new ScalaTemplateManager()
  
  private val projects = new HashMap[IProject, ScalaProject]
  
  override def start(context : BundleContext) = {
    super.start(context)
    Tracer.println("start plugin")
    ResourcesPlugin.getWorkspace.addResourceChangeListener(this, IResourceChangeEvent.PRE_CLOSE | IResourceChangeEvent.POST_CHANGE)
    JavaCore.addElementChangedListener(this)
    Platform.getContentTypeManager.
      getContentType(JavaCore.JAVA_SOURCE_CONTENT_TYPE).
        addFileSpec("scala", IContentTypeSettings.FILE_EXTENSION_SPEC)
    Util.resetJavaLikeExtensions // TODO Is this still needed?
    PlatformUI.getWorkbench.getEditorRegistry.setDefaultEditor("*.scala", editorId)
    PerspectiveFactory.updatePerspective
  }

  override def stop(context : BundleContext) = {
    ResourcesPlugin.getWorkspace.removeResourceChangeListener(this)

    super.stop(context)
  }
  
  def workspaceRoot = ResourcesPlugin.getWorkspace.getRoot
    
  def getJavaProject(project : IProject) = JavaCore.create(project) 

  def getScalaProject(project : IProject) : ScalaProject = projects.synchronized {
    projects.get(project) match {
      case Some(scalaProject) => scalaProject
      case None =>
        val scalaProject = new ScalaProject(project)
        projects(project) = scalaProject
        scalaProject
    }
  }
  
  def getScalaProject(input : IEditorInput) : ScalaProject = input match {
    case fei : IFileEditorInput => getScalaProject(fei.getFile.getProject)
    case cfei : IClassFileEditorInput => getScalaProject(cfei.getClassFile.getJavaProject.getProject)
    case _ => null
  }

  def isScalaProject(project: IJavaProject): Boolean = isScalaProject(project.getProject)
  
  def isScalaProject(project : IProject): Boolean =
    try {
      project != null && project.isOpen && (project.hasNature(natureId) || project.hasNature(oldNatureId))
    } catch {
      case _ : CoreException => false
    }

  override def resourceChanged(event : IResourceChangeEvent) {
    (event.getResource, event.getType) match {
      case (project : IProject, IResourceChangeEvent.PRE_CLOSE) => 
        projects.synchronized{ projects.remove(project) }
      case _ =>
    }
  }

  override def elementChanged(event : ElementChangedEvent) {
    def topLevelRemoved(ssf : ScalaSourceFile) {
      val project = getScalaProject(ssf.getJavaProject.getProject) 
      project.scheduleResetPresentationCompiler
      project.forceClean = true
    }
    
    val delta = event.getDelta
    delta.getElement match {
      case ssf : ScalaSourceFile if (delta.getKind == IJavaElementDelta.CHANGED) =>
        if (delta.getAffectedChildren.exists(_.getKind == IJavaElementDelta.REMOVED))
          topLevelRemoved(ssf)
      case _ : JavaModel =>
        def findRemovedSource(deltas : Array[IJavaElementDelta]) : Boolean =
          deltas.exists { delta =>
            delta.getElement match {
              case ssf : ScalaSourceFile if (delta.getKind == IJavaElementDelta.REMOVED) =>
                topLevelRemoved(ssf)
                true
              case _ : PackageFragment | _ : PackageFragmentRoot | _ : JavaProject =>
                findRemovedSource(delta.getAffectedChildren)
              case _ => false
            }
          }
        findRemovedSource(delta.getAffectedChildren)
      case _ =>
    }
  }

  def logWarning(msg : String) : Unit = getLog.log(new Status(IStatus.WARNING, pluginId, msg))

  def logError(t : Throwable) : Unit = logError(t.getClass + ":" + t.getMessage, t)
  
  def logError(msg : String, t : Throwable) : Unit = {
    val t1 = if (t != null) t else { val ex = new Exception ; ex.fillInStackTrace ; ex }
    val status1 = new Status(IStatus.ERROR, pluginId, IStatus.ERROR, msg, t1)
    getLog.log(status1)

    val status = t match {
      case ce : ControlThrowable =>
        val t2 = { val ex = new Exception ; ex.fillInStackTrace ; ex }
        val status2 = new Status(
          IStatus.ERROR, pluginId, IStatus.ERROR,
          "Incorrectly logged ControlThrowable: "+ce.getClass.getSimpleName+"("+ce.getMessage+")", t2)
        getLog.log(status2)
      case _ =>
    }
  }
  
  def bundlePath = check {
    val bundle = getBundle 
    val bpath = bundle.getEntry("/")
    val rpath = FileLocator.resolve(bpath)
    rpath.getPath
  }.getOrElse("unresolved")

  final def check[T](f : => T) =
    try {
      Some(f)
    } catch {
      case e : Throwable =>
        logError(e)
        None
    }

  def isBuildable(file : IFile) = (file.getName.endsWith(scalaFileExtn) || file.getName.endsWith(javaFileExtn))
}
