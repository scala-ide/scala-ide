/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import scala.collection.JavaConversions._
import scala.collection.mutable.{ LinkedHashMap, LinkedHashSet, HashMap, HashSet }

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream, ObjectInputStream, ObjectOutputStream }
import java.io.File.pathSeparator

import org.eclipse.core.resources.{ IContainer, IFile, IFolder, IMarker, IProject, IResource, IResourceChangeEvent, IResourceChangeListener, IWorkspaceRunnable, ResourcesPlugin}
import org.eclipse.core.runtime.{ CoreException, FileLocator, IPath, IProgressMonitor, IStatus, Path, Platform, Status }
import org.eclipse.core.runtime.content.IContentTypeSettings
import org.eclipse.jdt.core.{ IClassFile, IClasspathEntry, IJavaElement, IJavaProject, IPackageFragment, IPackageFragmentRoot, IType, JavaCore }
import org.eclipse.jdt.internal.core.{ BinaryType, JavaProject, PackageFragment }
import org.eclipse.jdt.internal.core.util.Util
import org.eclipse.jdt.internal.ui.IResourceLocator
import org.eclipse.jdt.internal.ui.javaeditor.{ ClassFileMarkerAnnotationModel, InternalClassFileEditorInput }
import org.eclipse.jdt.internal.ui.text.java.JavaCompletionProposal
import org.eclipse.jdt.ui.JavaUI
import org.eclipse.jface.dialogs.ErrorDialog
import org.eclipse.jface.text.{ IDocument, ITextViewer, Position, Region, TextPresentation }
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.jface.text.source.{ Annotation, IAnnotationModel }
import org.eclipse.jface.text.source.projection.{ ProjectionAnnotation, ProjectionAnnotationModel }
import org.eclipse.swt.SWT
import org.eclipse.swt.custom.StyleRange
import org.eclipse.swt.graphics.{ Color, Image }
import org.eclipse.swt.widgets.Display
import org.eclipse.ui.{ IEditorInput, IFileEditorInput, IWorkbenchPage, PlatformUI }
import org.eclipse.ui.ide.IDE
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.eclipse.ui.texteditor.ITextEditor
import org.osgi.framework.BundleContext

import scala.tools.nsc.{ Global, Settings }
import scala.tools.nsc.ast.parser.Scanners
import scala.tools.nsc.io.{ AbstractFile, PlainFile, ZipArchive }
   
import scala.tools.eclipse.properties.PropertyStore
import scala.tools.eclipse.util.{ IDESettings, Style } 

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
    
  //def oldId = "ch.epfl.lamp.sdt.core"  
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
 
  val noColor : Color = null
  private[eclipse] def savePreferenceStore0 = savePreferenceStore

  def workspace = ResourcesPlugin.getWorkspace.getRoot
  
  def project(name : String) : Option[Project] = workspace.findMember(name) match {
  case project : IProject => Some(projects.apply(project))
  case _ => None;
  }
  
  def bundlePath = check{
    val bundle = getBundle 
    val bpath = bundle.getEntry("/")
    val rpath = FileLocator.resolve(bpath)
    rpath.getPath
  }.getOrElse("unresolved")

  class DependMap extends LinkedHashMap[IPath,LinkedHashSet[IPath]] {
    override def default(key : IPath) = {
      val ret = new LinkedHashSet[IPath]
      this(key) = ret; ret
    }
  }
  /* private[eclipse] */ object reverseDependencies extends DependMap
  private val projects = new LinkedHashMap[IProject,Project] {
    override def default(key : IProject) = synchronized{
      val ret = Project(key)
      this(key) = ret; ret
    }
    override def apply(key : IProject) = synchronized{super.apply(key)}
    override def get(key : IProject) = synchronized{super.get(key)}
    override def removeKey(key : IProject) = synchronized{super.removeKey(key)}
  }
  
  def projectSafe(project : IProject) = if (project eq null) None else projects.get(project) match {
  case _ if !project.exists() || !project.isOpen => None
  case None if canBeConverted(project) => Some(projects(project))
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
  //protected def log(status : Status) = getLog.log(status)
  
  def getFile(path : IPath) : Option[Project#File] = workspace.getFile(path) match {
    case file if file.exists =>
      projectSafe(file.getProject) match {
        case Some(project) => project.fileSafe(file)
        case None => None
      }
    case _ => None
  }
  
  def fileFor(file0 : IFile) : Option[Project#File] = projectSafe(file0.getProject) match {
    case Some(project) => project.fileSafe(file0)
    case None => None
  }
  
  class PresentationContext {
    val invalidate = new HashMap[Int,Int]
    var remove = List[ProjectionAnnotation]()
    var modified = List[ProjectionAnnotation]()
    val add = new LinkedHashMap[ProjectionAnnotation,Position]
  }
  
  abstract class Hyperlink(offset : Int, length : Int) extends IHyperlink {
    def getHyperlinkRegion = new Region(offset, length)
  }
  
  /* private[eclipse] */ val viewers = new LinkedHashMap[Project#File,ScalaSourceViewer]
  
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
  
  protected def timeout : Long = 50 // milliseconds
  
  trait FixedInput extends IEditorInput {
    val project : Project    
    def initialize(doc : IDocument) : Unit
    def neutralFile : Project#File
    def createAnnotationModel : IAnnotationModel = new ProjectionAnnotationModel
  }
  
  def fileFor(input : IEditorInput) : Option[Project#File] = input match {
    case input : FixedInput => Some(input.neutralFile)
    case input : IFileEditorInput => fileFor(input.getFile)
    case _ => None
  }

  override def initializeDefaultPreferences(store0 : org.eclipse.jface.preference.IPreferenceStore) = {
    super.initializeDefaultPreferences(store0)
    Style.initializeEditorPreferences
  }
  
  def bundle : java.util.ResourceBundle = MyBundle
  protected object MyBundle extends java.util.ResourceBundle {
    def getKeys = new java.util.Enumeration[String] {
      def nextElement = throw new Error;
      def hasMoreElements = false;
    }
    def handleGetObject(str : String) : Object = throw new java.util.MissingResourceException("","","");
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

  def Project(underlying : IProject) = new Project(underlying)
  
  class Project(val underlying : IProject) extends CompilerProject {

    val ERROR_TYPE = "lampion.error"

    override def toString = underlying.getName
    
    def isOpen = underlying.isOpen
    
    /* when a file needs to be rooted out */
    def buildDone(built : HashSet[File], monitor : IProgressMonitor) : Unit = if (!built.isEmpty) {
      lastBuildHadBuildErrors = built.exists(_.hasBuildErrors)
      val manager = (underlying.findMember(".manager") match {
      case null => null 
      case fldr : IFolder => fldr
      case file : IFile => 
        file.delete(true, monitor)
        null
      case other =>
        logError("unexpected: " + other, null)
        other.delete(true, monitor)
        null
      }) match {
      case null =>
        val manager = underlying.getFolder(".manager")
        manager.create(true, true, monitor)
        manager.setDerived(true); manager
      case fldr => fldr
      }
      built.foreach{file =>
        val useFile = file.buildInfo(manager)
        val bos = new ByteArrayOutputStream
        file.saveBuildInfo(new DataOutputStream(bos))
        val bis = new ByteArrayInputStream(bos.toByteArray)
        if (useFile.exists) try {
          useFile.delete(true, monitor)
        } catch {case t =>
          logError("file: " + useFile,t)
        }
        useFile.create(bis, true, monitor)
        useFile.setDerived(true)
      }
    }
    def buildError0(severity : Int, msg : String, monitor : IProgressMonitor) = if (problemMarkerId.isDefined) {
      underlying.getWorkspace.run(new IWorkspaceRunnable {
        def run(monitor : IProgressMonitor) = {
          val mrk = underlying.createMarker(problemMarkerId.get)
          mrk.setAttribute(IMarker.SEVERITY, severity)
          val string = msg.map{
            case '\n' => ' '
            case '\r' => ' '
            case c => c
          }.mkString("","","")
          mrk.setAttribute(IMarker.MESSAGE , msg)
        }
      }, monitor)
    }
    def clearBuildErrors(monitor : IProgressMonitor) = if (problemMarkerId.isDefined) {
      underlying.getWorkspace.run(new IWorkspaceRunnable {
        def run(monitor : IProgressMonitor) = {
          underlying.deleteMarkers(problemMarkerId.get, true, IResource.DEPTH_ZERO)
        }
      }, monitor)
    }
    
    var lastBuildHadBuildErrors = false
    
    private val files = new LinkedHashMap[IFile,File] {
      override def default(key : IFile) = {
        assert(key != null)
        val ret = File(key); this(key) = ret; 
        val manager = Project.this.underlying.getFolder(".manager")
        ret.checkBuildInfo(manager)
        ret
      }
    }
    def fileSafe(file : IFile) : Option[File] = files.get(file) match{
    case _ if !file.exists => None
    case None if canBeConverted(file) => Some(files(file))
    case ret => ret
    }
    
    protected def findFile(path : String) = {
      val file = underlying.getFile(path)
      if (!file.exists) throw new Error
      fileSafe(underlying.getFile(path)).get
    }

    /* private[eclipse] */ var doFullBuild = false

    def inUIThread = Display.getCurrent != null
    
    def initialize(viewer : ScalaSourceViewer) : Unit = {

    }

    def Hyperlink(file : File, offset : Int, length : Int)(action : => Unit)(info : String) = new Hyperlink(offset, length) {
      def open = {
        action
      }
      def getHyperlinkText = info
      def getTypeLabel = null
    }
    
              
    private def sys(code : Int) = Display.getDefault().getSystemColor(code)
    
    def highlight(sv : ScalaSourceViewer, offset0 : Int, length0 : Int, style0 : Style, txt : TextPresentation) : Unit = {
      if (sv == null || sv.getTextWidget == null || sv.getTextWidget.isDisposed) return
      //val offset = sv.modelOffset2WidgetOffset(offset0)
      //val length = sv.modelOffset2WidgetOffset(offset0 + length0) - offset
      val extent = txt.getExtent
      val offset = offset0
      if (offset >= extent.getOffset + extent.getLength) return
      val length = if (offset + length0 <= extent.getOffset + extent.getLength) length0
                   else return // extent.getOffset + extent.getLength - offset
        
      if (offset == -1 || length <= 0) return
      val range = new StyleRange
      range.length = length
      val style = style0
      range.foreground = style.foreground // could be null
      range.background = style.background 
        
      range.underline = style.underline
      range.strikeout = style.strikeout
      range.fontStyle = (if (style.bold) SWT.BOLD else SWT.NORMAL) |
        (if (style.italics) SWT.ITALIC else SWT.NORMAL)
      range.start = offset
      txt mergeStyleRange range
    }
    
    def hover(file : File, offset : Int) : Option[RandomAccessSeq[Char]] = {
      Some("Not yet implemented") // TODO reinstate
    }

    def hyperlink(file : File, offset : Int) : Option[IHyperlink] = {
      None // TODO reinstate
    }

    def openAndSelect(file : File, select : => (Int,Int)) : Unit = {
      file.doLoad
      val editor =
        if (file.isLoaded)  file.editor.get else { 
          val wb = PlatformUI.getWorkbench
          val page = wb.getActiveWorkbenchWindow.getActivePage
          val e = file.doLoad0(page)
          if (e eq null) {
            logError("cannot load " + file, null)
            return
          }
          e.asInstanceOf[ITextEditor]
        }
        
      val site = editor.getSite
      val page = site.getPage
      if (!page.isPartVisible(editor)) file.doLoad0(page)
      val (offset,length) = select
      editor.selectAndReveal(offset, length)
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
    
    def externalDepends = underlying.getReferencedProjects 

    def self : Project = this
    assert(underlying != null) // already initialized, I hope!
    assert(underlying.hasNature(natureId))
    assert(JavaProject.hasJavaNature(underlying))
    def javaProject = JavaCore.create(underlying)
    def sourceFolders = ScalaPlugin.this.sourceFolders(javaProject)
    
    def outputPath = outputPath0.toOSString
    def outputPath0 = check {
      val outputLocation = javaProject.getOutputLocation
      val cntnr = workspace.findMember(outputLocation)
      assert(cntnr ne null)
      
      val project = javaProject.getProject
      if (cntnr != project && !ResourcesPlugin.getWorkspace.isTreeLocked) cntnr match {
        case fldr : IFolder =>
          def createParentFolder(parent : IContainer) {
            if(!parent.exists()) {
              createParentFolder(parent.getParent)
              parent.asInstanceOf[IFolder].create(true, true, null)
              parent.setDerived(true)
            }
          }
        
          fldr.refreshLocal(IResource.DEPTH_ZERO, null)
          if(!fldr.exists()) {
            createParentFolder(fldr.getParent)
            fldr.create(IResource.FORCE | IResource.DERIVED, true, null)
          }
        case _ => 
      }

      cntnr.getLocation
    } getOrElse underlying.getLocation
    
    def dependencies = {
      javaProject.getAllPackageFragmentRoots.elements.filter(p => {
        check(p.getKind == IPackageFragmentRoot.K_SOURCE && (p.getResource match {
        case p : IProject if JavaProject.hasJavaNature(p) => true
        case _ => false
        })) getOrElse false
      }).map(p => JavaCore.create(p.getResource.asInstanceOf[IProject]))
    }
    private var classpathUpdate : Long = IResource.NULL_STAMP
    def checkClasspath : Unit = check {
      val cp = underlying.getFile(".classpath")
      if (cp.exists) classpathUpdate match {
      case IResource.NULL_STAMP => classpathUpdate = cp.getModificationStamp()
      case stamp if stamp == cp.getModificationStamp() => 
      case _ =>
        classpathUpdate = cp.getModificationStamp()
        resetCompiler
      }
    }
    def resetCompiler = {
      buildCompiler = null
      // XXX: nothing we can do for presentation compiler.
    } 

    object compiler extends Global(new Settings(null)) with Scanners {
      override val global = this    // For Scanners
      Project.this.initialize(this)

      def plugin = ScalaPlugin.this
      def project = Project.this.self
       
      override def logError(msg : String, t : Throwable) =
        ScalaPlugin.this.logError(msg, t)
    }
    
    private implicit def r2o[T <: AnyRef](x : T) = if (x == null) None else Some(x)
    override def charSet(file : PlainFile) : String = nscToEclipse(file).getCharset

    //override def logError(msg : String, e : Throwable) : Unit =
    //  ScalaPlugin.this.logError(msg,e)
    override def buildError(file : PlainFile, severity0 : Int, msg : String, offset : Int, identifier : Int) : Unit =
      nscToLampion(file).buildError({
        severity0 match { //hard coded constants from reporters
          case 2 => IMarker.SEVERITY_ERROR
          case 1 => IMarker.SEVERITY_WARNING
          case 0 => IMarker.SEVERITY_INFO
        }
      }, msg, offset, identifier, null)
    
    override def buildError(severity0 : Int, msg : String) = buildError0(severity0, msg, null)
    
    override def clearBuildErrors(file : AbstractFile) : Unit  = {
      nscToLampion(file.asInstanceOf[PlainFile]).clearBuildErrors(null)
      clearBuildErrors(null : IProgressMonitor)
    }
    override def clearBuildErrors() = clearBuildErrors(null : IProgressMonitor)
    
    override def hasBuildErrors(file : PlainFile) : Boolean = 
      nscToLampion(file).hasBuildErrors

    override def projectFor(path : String) : Option[Project] = {
      val root = ResourcesPlugin.getWorkspace.getRoot.getLocation.toOSString
      if (!path.startsWith(root)) return None
      val path1 = path.substring(root.length)
      
      val res = ResourcesPlugin.getWorkspace.getRoot.findMember(Path.fromOSString(path1))
      projectSafe(res.getProject)
    }
    override def fileFor(path : String) : PlainFile = {
      val root = ResourcesPlugin.getWorkspace.getRoot.getLocation.toOSString
      assert(path.startsWith(root))
      val path1 = path.substring(root.length)
      val file = ResourcesPlugin.getWorkspace.getRoot.getFile(Path.fromOSString(path1))
      assert(file.exists)
      new PlainFile(new java.io.File(file.getLocation.toOSString))
    }
    override def signature(file : PlainFile) : Long = {
      nscToLampion(file).signature
    }
    override def setSignature(file : PlainFile, value : Long) : Unit = {
      nscToLampion(file).signature = value
    }
    override def refreshOutput : Unit = {
      val res = workspace.findMember(javaProject.getOutputLocation)
      res.refreshLocal(IResource.DEPTH_INFINITE, null)
    }
    override def dependsOn(file : PlainFile, what : PlainFile) : Unit = {
      val f0 = nscToLampion(file)
      val p1 = what.file.getAbsolutePath
      val p2 = Path.fromOSString(p1)
      f0.dependsOn(p2)
    }
    override def resetDependencies(file : PlainFile) = {
      nscToLampion(file).resetDependencies
    }
    
    override def initialize(global : Global) = {
      val settings = new Settings(null)
      val sourceFolders = this.sourceFolders
      val sourcePath = sourceFolders.map(_.getLocation.toOSString).mkString("", pathSeparator, "")
      settings.sourcepath.tryToSetFromPropertyValue(sourcePath)
      settings.outdir.tryToSetFromPropertyValue(outputPath)
      settings.classpath.tryToSetFromPropertyValue("")     // Is this really needed?
      settings.bootclasspath.tryToSetFromPropertyValue("") // Is this really needed?
      if (!sourceFolders.isEmpty) {
        settings.encoding.value = sourceFolders.elements.next.getDefaultCharset
      }
      settings.deprecation.value = true
      settings.unchecked.value = true
      //First check whether to use preferences or properties
      //TODO - should we rely on ScalaPlugin?  Well.. we need these preferences...
      val workspaceStore = ScalaPlugin.plugin.getPreferenceStore
      val projectStore = new PropertyStore(underlying, workspaceStore, pluginId)
      val useProjectSettings = projectStore.getBoolean(SettingConverterUtil.USE_PROJECT_SETTINGS_PREFERENCE)
      
      val store = if (useProjectSettings) projectStore else workspaceStore  
      IDESettings.shownSettings(settings).foreach {
	      setting =>
          val value = store.getString(SettingConverterUtil.convertNameToProperty(setting.name))
          try {          
            if (value != null)
              setting.tryToSetFromPropertyValue(value)
          } catch {
            case t : Throwable => logError("Unable to set setting '"+setting.name+"'", t)
          }
      }
      global.settings = settings
      global.classPath.entries.clear // Is this really needed? Why not filter out from allSettings?
      // setup the classpaths!
      intializePaths(global)
    }
    def intializePaths(global : nsc.Global) = {
      val sourceFolders = this.sourceFolders
      val sourcePath = sourceFolders.map(_.getLocation.toOSString).mkString("", pathSeparator, "")
      global.classPath.output(outputPath, sourcePath)
      val cps = javaProject.getResolvedClasspath(true)
      cps.foreach(cp => check{cp.getEntryKind match { 
      case IClasspathEntry.CPE_PROJECT => 
        val path = cp.getPath
        val p = ScalaPlugin.this.javaProject(workspace.getProject(path.lastSegment))
        if (!p.isEmpty) {
          if (p.get.getOutputLocation != null) {
            val classes = resolve(p.get.getOutputLocation).toOSString
            val sources = ScalaPlugin.this.sourceFolders(p.get).map(_.getLocation.toOSString).mkString("", pathSeparator, "")
            global.classPath.library(classes, sources)
          }
          p.get.getAllPackageFragmentRoots.elements.filter(!_.isExternal).foreach{root =>
            val cp = JavaCore.getResolvedClasspathEntry(root.getRawClasspathEntry)
            if (cp.isExported) {
              val classes = resolve(cp.getPath).toOSString
              val sources = cp.getSourceAttachmentPath.map(p => resolve(p).toOSString).getOrElse(null)
              global.classPath.library(classes, sources)
            }
          }
        }
      case IClasspathEntry.CPE_LIBRARY =>   
        val classes = resolve(cp.getPath).toOSString
        val sources = cp.getSourceAttachmentPath.map(p => resolve(p).toOSString).getOrElse(null)
        global.classPath.library(classes, sources)
      case IClasspathEntry.CPE_SOURCE =>  
      case _ => 
      }})
    }
    
    def File(underlying : IFile) = new File(underlying)
    
    class File(val underlying : IFile) {
      def self : File = this
      private[eclipse] var signature : Long = 0

      def viewer : Option[ScalaSourceViewer] = viewers.get(self)
      def editor : Option[Editor] = viewer.map(_.editor)
      
      var dependencies = new LinkedHashSet[IPath]
      private var infoLoaded : Boolean = false
      def project0 : Project = Project.this
      def project : Project = Project.this.self
      override def toString = underlying.toString

      def doComplete(offset : Int) : List[ICompletionProposal] = Nil // TODO reinstate
      
      def checkBuildInfo(manager : IFolder) = if (!infoLoaded) {
        infoLoaded = true
        if (manager.exists) {
          val useFile = buildInfo(manager)
          if (useFile.exists) {
            loadBuildInfo(new DataInputStream(useFile.getContents(true)))
          }
        }
      }
      
      def buildInfo(manager : IFolder) : IFile = {
        var str = underlying.getLocation.toString
        var idx = str.indexOf('/')
        while (idx != -1) {
          str = str.substring(0, idx) + "_$_" + str.substring(idx + 1, str.length)
          idx = str.indexOf('/')
        }
        manager.getFile(str)
      }
      
      def resetDependencies = {
        val filePath = underlying.getLocation
        dependencies.foreach(reverseDependencies(_) -= filePath)
        dependencies.clear
      }
      
      def dependsOn(path : IPath) = {
          dependencies += path
          reverseDependencies(path) += underlying.getLocation
      }
      
      def clearBuildErrors(monitor : IProgressMonitor) =
        if (problemMarkerId.isDefined)
          underlying.getWorkspace.run(new IWorkspaceRunnable {
            def run(monitor : IProgressMonitor) = underlying.deleteMarkers(problemMarkerId.get, true, IResource.DEPTH_INFINITE)
          }, monitor)
      
      def hasBuildErrors : Boolean =
        !problemMarkerId.isEmpty && underlying.findMarkers(problemMarkerId.get, true, IResource.DEPTH_INFINITE).exists(_.getAttribute(IMarker.SEVERITY) == IMarker.SEVERITY_ERROR)
      
      def buildError(severity : Int, msg : String, offset : Int, length : Int, monitor : IProgressMonitor) = if (problemMarkerId.isDefined) {
        underlying.getWorkspace.run(new IWorkspaceRunnable {
          def run(monitor : IProgressMonitor) = {
            val mrk = underlying.createMarker(problemMarkerId.get)
            mrk.setAttribute(IMarker.SEVERITY, severity)
            val string = msg.map{
              case '\n' => ' '
              case '\r' => ' '
              case c => c
            }.mkString("","","")
            
            mrk.setAttribute(IMarker.MESSAGE , msg)
            if (offset != -1) {
              mrk.setAttribute(IMarker.CHAR_START, offset)
              mrk.setAttribute(IMarker.CHAR_END  , offset + length)
              val line = toLine(offset)
              if (!line.isEmpty) 
                mrk.setAttribute(IMarker.LINE_NUMBER, line.get)
            }
          }
        }, monitor)
      }
      
      def toLine(offset : Int) : Option[Int] = None
      
      def Annotation(kind : String, text : String, offset : => Option[Int], length : Int) : Annotation = {
        val a = new Annotation(kind, false, text)
        asyncUI{
          val model = editor.map(_.getSourceViewer0.getAnnotationModel) getOrElse null
          val offset0 = offset
          if (model != null && offset0.isDefined) {
            model.addAnnotation(a, new Position(offset0.get, length))
          } 
        }
        a
      }
      
      def delete(a : Annotation) : Unit = asyncUI{
        val model = editor.map(_.getSourceViewer0.getAnnotationModel) getOrElse null
        if (model != null) model.removeAnnotation(a)
      }
      
      def highlight(offset0 : Int, length : Int, style : Style, txt : TextPresentation) : Unit = {
        val viewer = this.viewer
        if (viewer.isEmpty) return
        val sv = viewer.get
        Project.this.highlight(sv, offset0, length, style, txt)
      }

      def invalidate(start : Int, end : Int, txt : PresentationContext) : Unit = {
        txt.invalidate.get(start) match {
          case Some(end0) =>
            if (end > end0) txt.invalidate(start) = end
          case None => txt.invalidate(start) = end
        }
      }

      private object content0 extends RandomAccessSeq[Char] {
        private def doc = viewer.get.getDocument
        def length = doc.getLength
        def apply(idx : Int) = doc.getChar(idx)
      }

      def content : RandomAccessSeq[Char] = if (viewer.isDefined) content0 else 
        throw new Error(this + " not open for editing")

      def createPresentationContext : PresentationContext = new PresentationContext

      def finishPresentationContext(txt : PresentationContext) : Unit = if (!viewer.isEmpty) {
        val viewer = this.viewer.get
        if (viewer.getProjectionAnnotationModel != null) 
          viewer.getProjectionAnnotationModel.replaceAnnotations(txt.remove.toArray,txt.add)
        // highlight
        val i = txt.invalidate.elements
        if (!i.hasNext) return
        var current = i.next
        var toInvalidate = List[(Int,Int)]()
        def flush = {
          toInvalidate = current :: toInvalidate
          current = null
        }
        while (i.hasNext) {
          val (start,end) = i.next
          assert(start > current._1)
          if (false && end >= current._2) current = (current._1, end)
          else if (start <= current._2) {
            if (end > current._2) current = (current._1, end)
          } else {
            flush
            current = (start,end)
          }
        }
        flush
        if (inUIThread) {
          val i = toInvalidate.elements
          while (i.hasNext) i.next match {
          case (start,end) => viewer.invalidateTextPresentation(start, end - start)
          }
        } else {
          val display = Display.getDefault
          display.asyncExec(new Runnable { // so we happen later.
            def run = toInvalidate.foreach{
            case (start,end) => viewer.invalidateTextPresentation(start, end - start)
          }
          })
        }
      }
        
      def doPresentation : Unit = {
        // TODO reinstate
      }
      
      def isLoaded = viewers.contains(self)
      
      def doLoad : Unit =
        if (!isLoaded) {
          val wb = PlatformUI.getWorkbench
          val page = wb.getActiveWorkbenchWindow.getActivePage
          val editor = doLoad0(page)
          if(editor.isInstanceOf[Editor] && !isLoaded)
            logError("can't load: " + this,null)
        }
      
      def doUnload : Unit =
        if(isLoaded)
          viewers.removeKey(self)
      
      def newError(msg : String) = new Annotation(ERROR_TYPE, false, msg)
      
      def isAt(a : Annotation, offset : Int) : Boolean = {
        val model = editor.get.getSourceViewer0.getAnnotationModel
        if (model != null) {
          val pos = model.getPosition(a)
          pos != null && pos.getOffset == offset
        } else false
      }
        
      def install(offset : Int, length : Int, a : Annotation) = {
        val sv = editor.get.getSourceViewer0
        if (sv.getAnnotationModel != null)
          (sv.getAnnotationModel.addAnnotation(a, new org.eclipse.jface.text.Position(offset, length)))
      }
        
      def uninstall(a : Annotation) : Unit = {
        if (editor.isEmpty) return
        val sv = editor.get.getSourceViewer0
        if (sv.getAnnotationModel != null) {
          sv.getAnnotationModel.removeAnnotation(a)
          a.markDeleted(true)
        }
      }
      
      def Completion(offset : Int, length : Int, text : String, 
        info : Option[String], image : Option[Image], additional : => Option[String]) = {
        new JavaCompletionProposal(text, offset, length, image getOrElse null, text + info.getOrElse(""), 0) {
          override def apply(viewer : ITextViewer, trigger : Char, stateMask : Int, offset : Int) {
            super.apply(viewer, trigger, stateMask, offset)
          }
        }
      }
      
      def nscFile : AbstractFile = new PlainFile(underlying.getLocation.toFile)
      
      def saveBuildInfo(output : DataOutputStream) : Unit = {
        val output0 = new ObjectOutputStream(output)
        output0.writeObject(dependencies.toList.map(_.toOSString))
        output.writeLong(signature)
      }
      def loadBuildInfo(input : DataInputStream) : Unit = {
        val input0 = new ObjectInputStream(input)
        val list = input0.readObject.asInstanceOf[List[String]]
        list.foreach(dependencies += Path.fromOSString(_))
        signature = input.readLong
      }
      
      def sourcePackage : Option[String] =
        sourceFolders.find(_.getLocation.isPrefixOf(underlying.getLocation)) match {
          case Some(fldr) =>
            var path = underlying.getLocation.removeFirstSegments(fldr.getLocation.segmentCount)
            path = path.removeLastSegments(1).removeTrailingSeparator
            Some(path.segments.mkString("", ".", ""))
          case None => None
        }
        
      def defaultClassDir = {
          val file = new PlainFile(new java.io.File(outputPath))    
          if (file.isDirectory) Some(file) else None
      }
      
      def doLoad0(page : IWorkbenchPage) = IDE.openEditor(page, underlying, true)
    }
    
    def nscToLampion(file : PlainFile) : File = {
      val path = Path.fromOSString(file.path)
      val files = workspace.findFilesForLocation(path)
      assert(!files.isEmpty)
      val file0 = files(0)
      val file1 = fileSafe(file0).get
      file1
    }
    
    def nscToEclipse(file : AbstractFile) = nscToLampion(file.asInstanceOf[PlainFile]).underlying
    
    def lampionToNSC(file : File) : PlainFile = {
      val file = new java.io.File(underlying.getLocation.toOSString)
      new PlainFile(file) 
    }
    
    private var buildCompiler : BuildCompiler = _
    
    def build(toBuild : HashSet[File], monitor : IProgressMonitor) : Seq[File] = {
      checkClasspath
      if (buildCompiler == null) {
        buildCompiler = new BuildCompiler(this) // causes it to initialize.
      }
      val toBuild0 = new LinkedHashSet[AbstractFile]
      toBuild.foreach{file =>
        toBuild0 += lampionToNSC(file)
      }
      
      buildCompiler.build(toBuild0, monitor)
      Nil
    }

    def clean(monitor : IProgressMonitor) = {
      if (!problemMarkerId.isEmpty)                
        underlying.deleteMarkers(problemMarkerId.get, true, IResource.DEPTH_INFINITE)
      doFullBuild = true
      val fldr =underlying.findMember(".manager")
      if (fldr != null && fldr.exists) {
        fldr.delete(true, monitor)
      }
      buildCompiler = null // throw out the compiler.
      // delete the class files in bin
      def delete(container : IContainer, deleteDirs : Boolean)(f : String => Boolean) : Unit =
        if (container.exists()) {
          container.members.foreach {
            case cntnr : IContainer =>
              if (deleteDirs) {
                try {
                  cntnr.delete(true, monitor) // might not work.
                } catch {
                  case _ =>
                    delete(cntnr, deleteDirs)(f)
                    if (deleteDirs)
                      try {
                        cntnr.delete(true, monitor) // try again
                      } catch {
                        case t => ScalaPlugin.this.logError(t)
                      }
                }
              }
              else
                delete(cntnr, deleteDirs)(f)
            case file : IFile if f(file.getName) =>
              try {
                file.delete(true, monitor)
              } catch {
                case t => ScalaPlugin.this.logError(t)
              }
            case _ => 
          }
        }
      
      val outputLocation = javaProject.getOutputLocation
      val resource = workspace.findMember(outputLocation)
      resource match {
        case container : IContainer => delete(container, container != javaProject.getProject)(_.endsWith(".class"))
        case _ =>
      }
    }

    protected def findJava(sym : compiler.Symbol) : Option[IJavaElement] = {
      if (sym == compiler.NoSymbol) None
      else if (sym.owner.isPackageClass) {
        val found = javaProject.findType(sym.owner.fullNameString('.'), sym.simpleName.toString, null : IProgressMonitor)
        if (found eq null) None
        else if (sym.isConstructor) {
          val params = sym.info.paramTypes.map(signatureFor).toArray
          found.getMethod(sym.nameString, params)
        }
        else Some(found)
      } else {
        findJava(sym.owner) match {
          case Some(owner : IType) =>
            var ret : Option[IJavaElement] = None
            implicit def coerce(c : IJavaElement) : Option[IJavaElement] = if (c eq null) None else Some(c)
            if (ret.isEmpty && sym.isMethod) {
              val params = sym.info.paramTypes.map(signatureFor).toArray
              val name = if (sym.isConstructor) sym.owner.nameString else sym.nameString 
              val methods = owner.findMethods(owner.getMethod(name, params))
              if ((methods ne null) && methods.length > 0)
                ret = methods(0)
            }
            if (ret.isEmpty && sym.isType) ret = owner.getType(sym.nameString)
            if (ret.isEmpty) ret = owner.getField(sym.nameString)
            ret
          case _ => None
        }
      }
    }
    
    def signatureFor(tpe : compiler.Type) : String = {
      import compiler.definitions._
      import org.eclipse.jdt.core.Signature._
      
      def signatureFor0(sym : compiler.Symbol) : String = {
        if (sym == ByteClass) return SIG_BYTE
        if (sym == CharClass) return SIG_CHAR
        if (sym == DoubleClass) return SIG_DOUBLE
        if (sym == FloatClass) return SIG_FLOAT
        if (sym == IntClass) return SIG_INT
        if (sym == LongClass) return SIG_LONG
        if (sym == ShortClass) return SIG_SHORT
        if (sym == BooleanClass) return SIG_BOOLEAN
        if (sym == UnitClass) return SIG_VOID
        if (sym == AnyClass) return "Ljava.lang.Object;"
        if (sym == AnyRefClass) return "Ljava.lang.Object;"
        return 'L' + sym.fullNameString.replace('/', '.') + ';'
      }
      
      tpe match {
        case tpe : compiler.PolyType if tpe.typeParams.length == 1 && tpe.resultType == ArrayClass.tpe => 
          "[" + signatureFor0(tpe.typeParams(0))
        case tpe : compiler.PolyType => signatureFor(tpe.resultType) 
        case tpe => signatureFor0(tpe.typeSymbol)  
      }
    }
    
    private def findFileFor(file : PlainFile) : Option[Project#File] = {
      val path = Path.fromOSString(file.path)
      val files = workspace.findFilesForLocation(path)
      if (files.isEmpty)
        None
      else {
        val file0 = files(0)   
        assert(file0.exists)
        val project = projectSafe(file0.getProject)
        if (project.isEmpty) return None // not in a valid project.
        
        val prPath = file0.getProjectRelativePath
        val fldr = project.get.sourceFolders.find(_.getProjectRelativePath.isPrefixOf(prPath))
        if (fldr.isEmpty) return None // Not in a source folder
        
        val project0 = project.get
        return project0.fileSafe(file0)
      }
    }
    
    trait IdeRef {
      def hyperlink : Unit
      def hover : Option[RandomAccessSeq[Char]]
      def symbol : Option[Global#Symbol]
    }

    case object NoRef extends IdeRef {
      def hyperlink : Unit = {}
      def hover : Option[RandomAccessSeq[Char]] = None
      override def symbol = None
    }    
    
    private case class JavaRef(elem : IJavaElement, symbol0 : compiler.Symbol) extends IdeRef {
      override def hover = try {
        val str = elem.getAttachedJavadoc(null)
        if (str eq null) None else Some(str)
      } catch {
        case ex => 
          ScalaPlugin.this.logError(ex)
          Some("Method added to Java class by Scala compiler.")
      }
      
      override def hyperlink = JavaUI.openInEditor(elem, true, true)
      
      override def symbol = Some(symbol0)
    }
    
    protected def javaRef(symbol : compiler.Symbol) : IdeRef = {
      val elem = findJava(symbol) match {
        case Some(elem) => elem
        case None => return NoRef
      }
      JavaRef(elem,symbol)
    }
  }
  
  protected def canBeConverted(file : IFile) : Boolean = 
    (file.getName.endsWith(scalaFileExtn) || file.getName.endsWith(javaFileExtn))

  protected def canBeConverted(project : IProject) : Boolean = 
    project.hasNature(natureId)
  
  private[eclipse] def scalaSourceFile(classFile : IClassFile) : Option[(Project,AbstractFile)] = {
    val source = classFile.getType.asInstanceOf[BinaryType].getSourceFileName(null)
    val project = projectSafe(classFile.getJavaProject.getProject)
    if (source != null && source.endsWith(scalaFileExtn) && project.isDefined) {
      val pkgFrag = classFile.getType.getPackageFragment.asInstanceOf[PackageFragment]
      val rootSource = pkgFrag.getPackageFragmentRoot.getSourceAttachmentPath.toOSString
      val fullSource = pkgFrag.names.mkString("", "" + java.io.File.separatorChar, "") + java.io.File.separatorChar + source
      val file = if (rootSource.endsWith(jarFileExtn)) {
        val jf = new java.io.File(rootSource)
        if (jf.exists && !jf.isDirectory) {
        val archive = ZipArchive.fromFile(jf)
        archive.lookupPath(fullSource,false)
      } else {
          logError("could not find jar file " + jf, null)
          return None
        } // xxxx.
      } else {
        val jf = new java.io.File(rootSource)
        assert(jf.exists && jf.isDirectory)
        val dir = PlainFile.fromFile(jf)
        dir.lookupPath(fullSource, false)
      }
      Some(project.get, file)
    } else None
  }

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
