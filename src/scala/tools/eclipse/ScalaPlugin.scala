/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import scala.collection.mutable.{ LinkedHashMap }

import org.eclipse.core.resources.{ IContainer, IFile, IProject, IResourceChangeEvent, IResourceChangeListener, ResourcesPlugin }
import org.eclipse.core.runtime.{ CoreException, FileLocator, IPath, IStatus, Platform, Status }
import org.eclipse.core.runtime.content.IContentTypeSettings
import org.eclipse.jdt.core.{ IJavaProject, IPackageFragmentRoot, JavaCore }
import org.eclipse.jdt.internal.core.JavaProject
import org.eclipse.jdt.internal.core.util.Util
import org.eclipse.jface.dialogs.ErrorDialog
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.widgets.Display
import org.eclipse.ui.{ IEditorInput, IFileEditorInput, PlatformUI }
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext

import scala.tools.eclipse.util.Style 

object ScalaPlugin { 
  var plugin : ScalaPlugin = _

  def isScalaProject(project : IProject) =
    try {
      project != null && project.isOpen && project.hasNature(plugin.natureId)
    } catch {
      case _ : CoreException => false
    }
}

class ScalaPlugin extends AbstractUIPlugin with IResourceChangeListener {
  assert(ScalaPlugin.plugin == null)
  ScalaPlugin.plugin = this
  
  val OverrideIndicator = "scala.overrideIndicator"  
  def pluginId = "ch.epfl.lamp.sdt.core"
  def wizardPath = pluginId + ".wizards"
  def wizardId(name : String) = wizardPath + ".new" + name
  def classWizId = wizardId("Class")
  def traitWizId = wizardId("Trait")
  def objectWizId = wizardId("Object")
  def applicationWizId = wizardId("Application")
  def projectWizId = wizardId("Project")
  def netProjectWizId = wizardId("NetProject")
    
  def builderId = pluginId + ".scalabuilder"
  def natureId = pluginId + ".scalanature"  
  def launchId = "ch.epfl.lamp.sdt.launching"
  val scalaLib = "SCALA_CONTAINER"
  val scalaHome = "SCALA_HOME"
  def scalaLibId  = launchId + "." + scalaLib
  def scalaHomeId = launchId + "." + scalaHome
  def launchTypeId = "scala.application"
  def problemMarkerId = Some(pluginId + ".marker")
  val scalaFileExtn = ".scala"
  val javaFileExtn = ".java"
  val jarFileExtn = ".jar"

  val ERROR_TYPE = "lampion.error"

  val noColor : Color = null
  
  private[eclipse] def savePreferenceStore0 = savePreferenceStore

  def workspace = ResourcesPlugin.getWorkspace.getRoot
  
  def project(name : String) : Option[ScalaProject] = workspace.findMember(name) match {
  case project : IProject => Some(projects.apply(project))
  case _ => None;
  }
  
  def bundlePath = check{
    val bundle = getBundle 
    val bpath = bundle.getEntry("/")
    val rpath = FileLocator.resolve(bpath)
    rpath.getPath
  }.getOrElse("unresolved")

  private val projects = new LinkedHashMap[IProject, ScalaProject] {
    override def default(key : IProject) = synchronized{
      val ret = new ScalaProject(key)
      this(key) = ret; ret
    }
    override def apply(key : IProject) = synchronized{super.apply(key)}
    override def get(key : IProject) = synchronized{super.get(key)}
    override def removeKey(key : IProject) = synchronized{super.removeKey(key)}
  }
  
  def projectSafe(project : IProject) = if (project eq null) None else projects.get(project) match {
    case _ if !project.exists() || !project.isOpen => None
    case None if project.hasNature(natureId) => Some(projects(project))
    case ret => ret
  }
  
  /** error logging */
  def logError(msg : String, t : Throwable) : Unit = {
    var tt = t
    if (tt == null) tt = try {
      throw new Error
    } catch {
      case e : Error => e
    }
    val status = new Status(IStatus.ERROR, pluginId, IStatus.ERROR, msg, tt)
    log(status)
  }
  
  final def logError(t : Throwable) : Unit = logError(null, t)
  
  final def check[T](f : => T) = try { Some(f) } catch {
    case e : Throwable => logError(e); None
  }

  def fileFor(input : IEditorInput) : Option[ScalaFile] = input match {
    case input : IFileEditorInput => Some(fileFor(input.getFile))
    case _ => None
  }
  
  def fileFor(file : IFile) = new ScalaFile(file)
  
  abstract class Hyperlink(offset : Int, length : Int) extends IHyperlink {
    def getHyperlinkRegion = new Region(offset, length)
  }
  
  val viewers = new LinkedHashMap[ScalaFile, ScalaSourceViewer]
  
  private var hadErrors = false

  protected def log(status : Status) = {
    getLog.log(status)
    if (!hadErrors) {
      hadErrors = true
      val display = Display.getDefault
      if (false && display != null) display.syncExec(new Runnable {
        def run = {
          val msg = "An error has occured in the Scala Eclipse Plugin.  Please submit a bug report at http://scala.epfl.ch/bugs, and remember to include the .metadata/.log file from your workspace directory.  If this problem persists in the short term, it may be possible to recover by performing a clean build.";
          if (display.getActiveShell != null) 
            ErrorDialog.openError(null,null,msg,status)
        }
      })
    }
  }
  
  override def initializeDefaultPreferences(store0 : org.eclipse.jface.preference.IPreferenceStore) = {
    super.initializeDefaultPreferences(store0)
    Style.initializeEditorPreferences
  }
  
  def sourceFolders(javaProject : IJavaProject) : Iterable[IContainer] = {
    val isOpen = javaProject.isOpen
    if (!isOpen) javaProject.open(null)
    javaProject.getAllPackageFragmentRoots.filter(p =>
      check(p.getKind == IPackageFragmentRoot.K_SOURCE && p.getResource.isInstanceOf[IContainer] && (p == javaProject || p.getParent == javaProject)) getOrElse false
    ).map(_.getResource.asInstanceOf[IContainer])
  }
  
  def javaProject(p : IProject) = 
    if (JavaProject.hasJavaNature(p)) Some(JavaCore.create(p))
    else None
    
  def resolve(path : IPath) : IPath = {
    assert(path != null)
    if (path.lastSegment == null) return path
    val res =
      if (path.lastSegment.endsWith(".jar") || path.lastSegment.endsWith(".zip"))
        workspace.getFile(path)
      else
        workspace.findMember(path)

    if ((res ne null) && res.exists) res.getLocation else path
  }

  def syncUI[T](f : => T) : T = {
    var result : Option[T] = None
    var exc : Throwable = null
    Display.getDefault.syncExec(new Runnable {
      override def run = try {
        result = Some(f)
      } catch {
        case ex => exc = ex
      }
    })
    if (exc != null)
      throw exc
    else
      result.get
  }
  
  def asyncUI(f : => Unit) : Unit = {
    var exc : Throwable = null
    Display.getDefault.asyncExec(new Runnable {
      override def run = try {
        f
      } catch {
      case ex => exc = ex
      }
    })
    if (exc != null) throw exc
  }
  
  def inUIThread = Display.getCurrent != null
  
  def canBeConverted(file : IFile) : Boolean = 
    (file.getName.endsWith(scalaFileExtn) || file.getName.endsWith(javaFileExtn))

  def editorId : String = "scala.tools.eclipse.Editor"

  override def start(context : BundleContext) = {
    super.start(context)
    
    ResourcesPlugin.getWorkspace.addResourceChangeListener(this, IResourceChangeEvent.PRE_CLOSE | IResourceChangeEvent.POST_CHANGE)
    ScalaIndexManager.initIndex(ResourcesPlugin.getWorkspace)
    Platform.getContentTypeManager.
      getContentType(JavaCore.JAVA_SOURCE_CONTENT_TYPE).
        addFileSpec("scala", IContentTypeSettings.FILE_EXTENSION_SPEC)
    Util.resetJavaLikeExtensions // TODO Is this still needed?
    PlatformUI.getWorkbench.getEditorRegistry.setDefaultEditor("*.scala", editorId)
  }
  
  override def stop(context : BundleContext) = {
    ResourcesPlugin.getWorkspace.removeResourceChangeListener(this)

    super.stop(context)
  }
  
  override def resourceChanged(event : IResourceChangeEvent) {
    (event.getResource, event.getType) match {
      case (iproject : IProject, IResourceChangeEvent.PRE_CLOSE) => 
        val project = projects.removeKey(iproject)
      case _ =>
    }
  }
}
