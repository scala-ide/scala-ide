/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse

import scala.collection.jcl.{ ArrayList, LinkedHashMap, LinkedHashSet, TreeMap }
import scala.xml.NodeSeq        

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream, ObjectInputStream, ObjectOutputStream }

import org.eclipse.core.resources.{ IContainer, IFile, IFolder, IMarker, IProject, IResource, IResourceChangeEvent, IResourceChangeListener, IResourceDelta, IResourceDeltaVisitor, IWorkspaceRunnable, ResourcesPlugin}
import org.eclipse.core.runtime.{ CoreException, FileLocator, IPath, IProgressMonitor, IStatus, Path, Platform, Status }
import org.eclipse.core.runtime.content.IContentTypeSettings
import org.eclipse.jdt.core.{ IClassFile, IClasspathEntry, IJavaProject, IPackageFragment, IPackageFragmentRoot, JavaCore }
import org.eclipse.jdt.internal.core.{ BinaryType, JavaProject, PackageFragment }
import org.eclipse.jdt.internal.core.util.Util
import org.eclipse.jdt.internal.ui.text.java.JavaCompletionProposal
import org.eclipse.jface.dialogs.ErrorDialog
import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.jface.text.{ Document, IDocument, IRepairableDocument, ITextViewer, Position, Region, TextPresentation }
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.jface.text.contentassist.ICompletionProposal
import org.eclipse.jface.text.source.{ Annotation, IAnnotationModel }
import org.eclipse.jface.text.source.projection.{ ProjectionAnnotation, ProjectionAnnotationModel, ProjectionViewer }
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics.{ Color, Image, RGB }
import org.eclipse.swt.widgets.Display
import org.eclipse.swt.custom.StyleRange
import org.eclipse.ui.{ IEditorInput, IEditorReference, IFileEditorInput, IPathEditorInput, IPersistableElement, IWorkbenchPage, PlatformUI }
import org.eclipse.ui.ide.IDE
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.eclipse.ui.texteditor.ITextEditor
import org.osgi.framework.BundleContext

import scala.tools.nsc.Settings
import scala.tools.nsc.io.{ AbstractFile, PlainFile, ZipArchive }
   
import lampion.presentation.Matchers
import scala.tools.editor.TypersPresentations
import scala.tools.eclipse.util.Colors
import scala.tools.eclipse.util.IDESettings
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

class ScalaPlugin extends {
  override val OverrideIndicator = "scala.overrideIndicator"  
} with AbstractUIPlugin with IResourceChangeListener with Matchers with TypersPresentations {
  assert(ScalaPlugin.plugin == null)
  ScalaPlugin.plugin = this
  
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
  
  trait FileSpec {
    def path : Option[IPath]
  }
  
  case class NormalFile(underlying : IFile) extends FileSpec {
    override def toString = underlying.getName  
    override def path = Some(underlying.getLocation)
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
  case None => None
  case Some(project) =>
    val file = project.fileSafe(file0)
    file
  }
  
  class PresentationContext { //(val presentation : TextPresentation) {
    val invalidate = new TreeMap[Int,Int]
    var remove = List[ProjectionAnnotation]()
    var modified = List[ProjectionAnnotation]()
    val add = new LinkedHashMap[ProjectionAnnotation,Position]
  }
  
  abstract class Hyperlink(offset : Int, length : Int) extends IHyperlink {
    def getHyperlinkRegion = new Region(offset, length)
  }
  
  /* private[eclipse] */ val viewers = new LinkedHashMap[Project#File,SourceViewer]
  
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
    import path.lastSegment
    if (lastSegment == null) return path
    val res =
      if (lastSegment.endsWith(".jar") || lastSegment.endsWith(".zip"))
        workspace.getFile(path)
      else
        workspace.findMember(path)

    if ((res ne null) && res.exists) res.getLocation else path
  }
  protected case class ClassFileSpec(source : AbstractFile, classFile : IClassFile) extends FileSpec {
    override def toString = source.name
    override def path = None // because they can't change or be recompiled.
  }

  def Project(underlying : IProject) = new Project(underlying)
  
  trait ProjectB extends super[TypersPresentations].ProjectImpl
  trait ProjectC extends super[Matchers].ProjectImpl
  class Project(val underlying : IProject) extends ProjectC with ProjectB with CompilerProject {

    val ERROR_TYPE = "lampion.error"
    val MATCH_ERROR_TYPE = "lampion.error.match"

    override def toString = underlying.getName
    override def isOpen = super.isOpen && underlying.isOpen  
    /* when a file needs to be rooted out */
    def buildDone(built : LinkedHashSet[File])(implicit monitor : IProgressMonitor) : Unit = if (!built.isEmpty) {
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
    def buildError0(severity : Int, msg : String)(implicit monitor : IProgressMonitor) = if (problemMarkerId.isDefined) {
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
    def clearBuildErrors(implicit monitor : IProgressMonitor) = if (problemMarkerId.isDefined) {
      underlying.getWorkspace.run(new IWorkspaceRunnable {
        def run(monitor : IProgressMonitor) = {
          underlying.deleteMarkers(problemMarkerId.get, true, IResource.DEPTH_ZERO)
        }
      }, monitor)
    }
    
    var lastBuildHadBuildErrors = false
    
    type Path = IPath
    
    private val files = new LinkedHashMap[IFile,File] {
      override def default(key : IFile) = {
        assert(key != null)
        val ret = File(NormalFile(key)); this(key) = ret; 
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

    override def inUIThread = Display.getCurrent != null
    
    def initialize(viewer : SourceViewer) : Unit = {

    }

    def Hyperlink(file : File, offset : Int, length : Int)(action : => Unit)(info : String) = new Hyperlink(offset, length) {
      def open = {
        action
        if (file.editing) file.processEdit
      }
      def getHyperlinkText = info
      def getTypeLabel = null
    }
    
              
    private def sys(code : Int) = Display.getDefault().getSystemColor(code)
    
    import org.eclipse.core.runtime.jobs._  
    import org.eclipse.core.runtime._  

    def highlight(sv : SourceViewer, offset0 : Int, length0 : Int, style0 : Style, txt : TextPresentation) : Unit = {
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
      txt addStyleRange range
    }
    
    def hover(file : File, offset : Int) : Option[RandomAccessSeq[Char]] = {
      val result = syncUI{
        file.tokenForFuzzy(offset)
      } 
      result.hover
    }

    def hyperlink(file : File, offset : Int) : Option[IHyperlink] = {
      val token = file.tokenForFuzzy(offset)
      token.hyperlink
    }
    override def openAndSelect(file : File, select : => (Int,Int)) : Unit = {
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
    
    override def syncUI[T](f : => T) : T = {
      val display = Display.getDefault
      if (Display.getCurrent == display) return f
      var result : T = null.asInstanceOf[T]
      var exc : Throwable = null
      display.syncExec(new Runnable {
        override def run = try {
          result = f
        } catch {
        case ex => exc = ex
        }
      })
      if (exc != null) throw exc
      else result
    }
    override def asyncUI(f : => Unit) : Unit = {
      val display = Display.getDefault
      if (Display.getCurrent == display) {
        f
        return
      }
      var exc : Throwable = null
      display.asyncExec(new Runnable {
        override def run = try {
          f
        } catch {
        case ex => exc = ex
        }
      })
      if (exc != null) throw exc
    }
    
    trait ParseNode extends ParseNodeImpl {
      def self : ParseNode
    } 
    trait IdentifierPosition extends IdentifierPositionImpl

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
    // needed to make the type gods happy
    trait Compiler2 extends super.Compiler{ self : compiler.type =>}
    object compiler0 extends nsc.Global(new Settings(null), new CompilerReporter) with Compiler2 with eclipse.Compiler {
      def plugin = ScalaPlugin.this
      def project = Project.this.self
      override def computeDepends(from : loaders.PackageLoader) = super[Compiler].computeDepends(from)
       
      override def logError(msg : String, t : Throwable) =
        ScalaPlugin.this.logError(msg, t)
      override def stale(path : String) : Seq[Symbol] = {
        val ret = super.stale(path)
        ret.foreach{sym => 
          assert(!sym.isModuleClass)
          assert(sym.owner != NoSymbol)
          // XXX: won't work.
          sym.owner.rawInfo.decls match {
          case scope : PersistentScope => scope.invalidate(sym.name)
          case _ =>  
          }
        }
        ret
      } 
      Project.this.initialize(this)
    }
    lazy val compiler : compiler0.type = compiler0
    import java.io.File.pathSeparator 
    
    private implicit def r2o[T <: AnyRef](x : T) = if (x == null) None else Some(x)
    override def charSet(file : PlainFile) : String = nscToEclipse(file).getCharset

    //override def logError(msg : String, e : Throwable) : Unit =
    //  ScalaPlugin.this.logError(msg,e)
    override def buildError(file : PlainFile, severity0 : Int, msg : String, offset : Int, identifier : Int) : Unit =
      nscToLampion(file).buildError({
        import IMarker._
        severity0 match { //hard coded constants from reporters
          case 2 => SEVERITY_ERROR
          case 1 => SEVERITY_WARNING
          case 0 => SEVERITY_INFO
        }
      }, msg, offset, identifier)(null)
    
    override def buildError(severity0 : Int, msg : String) = buildError0(severity0, msg)(null)
    
    override def clearBuildErrors(file : AbstractFile) : Unit  = {
      nscToLampion(file.asInstanceOf[PlainFile]).clearBuildErrors(null)
      clearBuildErrors(null:IProgressMonitor)
    }
    override def clearBuildErrors() = clearBuildErrors(null:IProgressMonitor)
    
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
    
    override def initialize(global : eclipse.Compiler) = {
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
      import SettingConverterUtil._
      import scala.tools.eclipse.properties.PropertyStore
      //TODO - should we rely on ScalaPlugin?  Well.. we need these preferences...
      val workspaceStore = ScalaPlugin.plugin.getPreferenceStore
      val projectStore = new PropertyStore(underlying, workspaceStore, pluginId)
      val useProjectSettings = projectStore.getBoolean(USE_PROJECT_SETTINGS_PREFERENCE)
      
      val store = if (useProjectSettings) projectStore else workspaceStore  
      IDESettings.shownSettings(settings).foreach {
	      setting =>
          val value = store.getString(convertNameToProperty(setting.name))
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
    
    def File(underlying : FileSpec) = new File(underlying)
    
    class File(val underlying : FileSpec) extends super[ProjectC].FileImpl with super[ProjectB].FileImpl {
      def self : File = this
      private[eclipse] var signature : Long = 0
      import java.io._

      def viewer : Option[SourceViewer] = viewers.get(self)
      def editor = viewer.map(_.editor) getOrElse None
      
      var dependencies = new LinkedHashSet[IPath]
      private var infoLoaded : Boolean = false
      def project0 : Project = Project.this
      def project : Project = Project.this.self
      override def toString = underlying.toString

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
        var str = underlying.path.get.toString
        var idx = str.indexOf('/')
        while (idx != -1) {
          str = str.substring(0, idx) + "_$_" + str.substring(idx + 1, str.length)
          idx = str.indexOf('/')
        }
        manager.getFile(str)
      }
      
      def resetDependencies = {
        val filePath = underlying.path.get
        dependencies.foreach(reverseDependencies(_) -= filePath)
        dependencies.clear
      }
      def dependsOn(path : IPath) = (underlying) match {
      case (NormalFile(self)) => 
        dependencies += path
        reverseDependencies(path) += self.getLocation
      case _ => 
      }
      def clearBuildErrors(implicit monitor : IProgressMonitor) = if (problemMarkerId.isDefined) {
        val file = underlying match {
        case NormalFile(file) => file
        }
        file.getWorkspace.run(new IWorkspaceRunnable {
          def run(monitor : IProgressMonitor) = {
            file.deleteMarkers(problemMarkerId.get, true, IResource.DEPTH_INFINITE)
          }
        }, monitor)
      }
      def hasBuildErrors : Boolean = if (problemMarkerId.isEmpty) false else {
        val file = underlying match {
        case NormalFile(file) => file
        }
        import IMarker.{ SEVERITY, SEVERITY_ERROR }
        file.findMarkers(problemMarkerId.get, true, IResource.DEPTH_INFINITE).exists(_.getAttribute(SEVERITY) == SEVERITY_ERROR)
      }
      
      def buildError(severity : Int, msg : String, offset : Int, length : Int)(implicit monitor : IProgressMonitor) = if (problemMarkerId.isDefined) {
        val file = underlying match {
          case NormalFile(file) => file
        }
        file.getWorkspace.run(new IWorkspaceRunnable {
          def run(monitor : IProgressMonitor) = {
            val mrk = file.createMarker(problemMarkerId.get)
            import IMarker._
            mrk.setAttribute(SEVERITY, severity)
            val string = msg.map{
              case '\n' => ' '
              case '\r' => ' '
              case c => c
            }.mkString("","","")
            
            mrk.setAttribute(MESSAGE , msg)
            if (offset != -1) {
              mrk.setAttribute(CHAR_START, offset)
              mrk.setAttribute(CHAR_END  , offset + length)
              val line = toLine(offset)
              if (!line.isEmpty) 
                mrk.setAttribute(LINE_NUMBER, line.get)
            }
          }
        }, monitor)
      }
      def toLine(offset : Int) : Option[Int] = None
      
      override def readOnly = underlying match {
      case NormalFile(_) => false
      case _ => super.readOnly
      }
      
      override def Annotation(kind : String, text : String, offset : => Option[Int], length : Int) : Annotation = {
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
      
      override def delete(a : Annotation) : Unit = asyncUI{
        val model = editor.map(_.getSourceViewer0.getAnnotationModel) getOrElse null
        if (model != null) model.removeAnnotation(a)
      }
      
      override def highlight(offset0 : Int, length : Int, style : Style, txt : TextPresentation) : Unit = {
        val viewer = this.viewer
        if (viewer.isEmpty) return
        val sv = viewer.get
        Project.this.highlight(sv, offset0, length, style, txt)
      }

      override def invalidate(start : Int, end : Int, txt : PresentationContext) : Unit = {
        txt.invalidate.get(start) match {
          case Some(end0) =>
            if (end > end0) txt.invalidate(start) = end
          case None => txt.invalidate(start) = end
        }
      }

      def refresh(offset : Int, length : Int, pres : TextPresentation) = {
        refreshHighlightFor(offset, length, pres)
      }
      
      private object content0 extends RandomAccessSeq[Char] {
        private def doc = viewer.get.getDocument
        def length = doc.getLength
        def apply(idx : Int) = doc.getChar(idx)
      }

      override def content : RandomAccessSeq[Char] = if (viewer.isDefined) content0 else 
        throw new Error(this + " not open for editing")

      override def createPresentationContext : PresentationContext = new PresentationContext

      override def finishPresentationContext(txt : PresentationContext) : Unit = if (!viewer.isEmpty) {
        val viewer = this.viewer.get
        if (viewer.projection != null) 
          viewer.projection.replaceAnnotations(txt.remove.toArray,txt.add.underlying)
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
      override def doPresentation : Unit = {
        if (this.viewer.isEmpty) return
        val viewer = this.viewer.get
        val oldBusy = viewer.busy
        viewer.busy = true
        try {
          super.doPresentation
        } catch {
          case ex =>
            ScalaPlugin.this.logError(ex)
        }
        finally {
          viewer.busy = oldBusy
        }
      }
      
      override def isLoaded = viewers.contains(self)
      override def doLoad : Unit = {
        matchErrors = Nil        
        if (!isLoaded) {
          val wb = PlatformUI.getWorkbench
          val page = wb.getActiveWorkbenchWindow.getActivePage
          val editor = doLoad0(page)
          if(editor.isInstanceOf[Editor]) {
            if (!isLoaded) {
              if (!isLoaded) {
                logError("can't load: " + this,null)
                return
              }
            }
            assert(isLoaded)
          }
        }
        super.doLoad
      }
      override def doUnload : Unit = {
        matchErrors = Nil        
        assert(isLoaded)
        viewers.removeKey(self)
        assert(!isLoaded)
        super.doUnload
      }
      override def newError(msg : String) = new Annotation(ERROR_TYPE, false, msg)
      override def isAt(a : Annotation, offset : Int) : Boolean = {
        val model = editor.get.getSourceViewer0.getAnnotationModel
        if (model != null) {
          val pos = model.getPosition(a)
          pos != null && pos.getOffset == offset
        } else false
      }
      override def install(offset : Int, length : Int, a : Annotation) = {
        val sv = editor.get.getSourceViewer0
        if (sv.getAnnotationModel != null)
          (sv.getAnnotationModel.addAnnotation(a, new org.eclipse.jface.text.Position(offset, length)))
      }
      override def uninstall(a : Annotation) : Unit = {
        if (editor.isEmpty) return
        val sv = editor.get.getSourceViewer0
        if (sv.getAnnotationModel != null) {
          sv.getAnnotationModel.removeAnnotation(a)
          a.markDeleted(true)
        }
      }
      
      type Completion = ICompletionProposal
      override def Completion(offset : Int, length : Int, text : String, 
          info : Option[String], image : Option[Image], additional : => Option[String]) = {
          new JavaCompletionProposal(text, offset, length, image getOrElse null, text + info.getOrElse(""), 0) {
            override def apply(viewer : ITextViewer, trigger : Char, stateMask : Int, offset : Int) {
              self.resetConstrict
              super.apply(viewer, trigger, stateMask, offset)
            }
          }
        }
      private var matchErrors = List[Annotation]() 
      override def removeUnmatched(offset : Int) = if (viewer.isDefined) {
        val v = viewer.get.getAnnotationModel
        matchErrors.find{a=>
          val pos = v.getPosition(a)
          pos != null && pos.offset == offset
        } match {
        case Some(a) => v.removeAnnotation(a)
                        matchErrors = matchErrors.filter(_ != a)
        case None =>
        }
      }
      override def addUnmatched(offset : Int, length : Int) = {
        val a = new Annotation(ERROR_TYPE, false, "unmatched")
        matchErrors = a :: matchErrors
        val v = viewer.get.getAnnotationModel
        v.addAnnotation(a, new org.eclipse.jface.text.Position(offset, length))
      }
      
      
      
      class IdentifierPosition extends Project.this.IdentifierPosition with IdentifierPositionImpl {
        override def self = this
      }
      override def IdentifierPosition = new IdentifierPosition
      class ParseNode extends Project.this.ParseNode with ParseNodeImpl {
        def self = this
        makeNoChanges
      }
      def ParseNode = new ParseNode
      override def Token(offset : Int, text : RandomAccessSeq[Char], code : Int) = new Token(offset : Int, text : RandomAccessSeq[Char], code : Int)
      class Token(val offset : Int, val text : RandomAccessSeq[Char], val code : Int) extends TokenImpl {
        def self = this
      }
      
      override def nscFile : AbstractFile = file.underlying match {
      case NormalFile(file) => new PlainFile(file.getLocation.toFile)
      case ClassFileSpec(source,clazz) => source
      }
      
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
      override def sourcePackage : Option[String] = underlying match {
      case NormalFile(file) => 
        sourceFolders.find(_.getLocation.isPrefixOf(file.getLocation)) match {
        case Some(fldr) =>
          var path = file.getLocation.removeFirstSegments(fldr.getLocation.segmentCount)
          path = path.removeLastSegments(1).removeTrailingSeparator
          Some(path.segments.mkString("", ".", ""))
        case None => super.sourcePackage
        }
      case ClassFileSpec(source,classFile) => 
        classFile.getParent match {
        case pkg : IPackageFragment => Some(pkg.getElementName)
        case _ => super.sourcePackage
        }
      case _ => super.sourcePackage
      }
      override def defaultClassDir = underlying match {
      case NormalFile(file) => 
        val file = new PlainFile(new java.io.File(outputPath))    
        if (file.isDirectory) Some(file)
        else super.defaultClassDir
      case ClassFileSpec(source,classFile) => 
        var p = classFile.getParent
        while (p != null && !p.isInstanceOf[IPackageFragmentRoot]) p = p.getParent
        p match {
        case null => super.defaultClassDir
        case p : IPackageFragmentRoot =>
          val path = p.getPath.toOSString
          if (path.endsWith(".jar") || path.endsWith(".zip"))
            Some(ZipArchive.fromFile(new java.io.File(path)))  
          else Some(new PlainFile(new java.io.File(path)))
        }
      case _ => super.defaultClassDir
      }
      
      var outlineTrees0 : List[compiler.Tree] = null
      def outlineTrees = {
        if (outlineTrees0 == null) outlineTrees0 = List(unloadedBody) 
        outlineTrees0
      }
      
      def doLoad0(page : IWorkbenchPage) = underlying match {
        case ClassFileSpec(source,clazz) => page.openEditor(new ClassFileInput(project,source,clazz), editorId) 
        case NormalFile(underlying) => IDE.openEditor(page, underlying, true)
      }
      
      override def parseChanged(node : ParseNode) = {
        super.parseChanged(node)
        //Console.println("PARSE_CHANGED: " + node)
        outlineTrees0 = rootParse.lastTyped
      }
      override def prepareForEditing = {
        super.prepareForEditing
        if (!viewer.isEmpty && viewer.get.projection != null) {
          val p = viewer.get.projection
          p.removeAllAnnotations
        }
        outlineTrees0 = rootParse.lastTyped
      }
    }
    
    import scala.tools.nsc.io.{AbstractFile,PlainFile}

    def nscToLampion(file : PlainFile) : File = {
      val path = Path.fromOSString(file.path)
      val files = workspace.findFilesForLocation(path)
      assert(!files.isEmpty)
      val file0 = files(0)
      val file1 = fileSafe(file0).get
      file1
    }
    
    def nscToEclipse(file : AbstractFile) = nscToLampion(file.asInstanceOf[PlainFile]).underlying match {
      case NormalFile(file) => file
    }
    
    def lampionToNSC(file : File) : PlainFile = {
      file.underlying match {
        case NormalFile(file) => 
          val ioFile = new java.io.File(file.getLocation.toOSString)
          assert(ioFile.exists)
          new PlainFile(ioFile) 
      }
    }
    
    private var buildCompiler : BuildCompiler = _ 
    def build(toBuild : LinkedHashSet[File])(implicit monitor : IProgressMonitor) : Seq[File] = {
      checkClasspath
      if (buildCompiler == null) {
        buildCompiler = new BuildCompiler(this) // causes it to initialize.
      }
      val toBuild0 = new LinkedHashSet[AbstractFile]
      toBuild.foreach{file =>
        toBuild0 += lampionToNSC(file)
      }
      val changed = buildCompiler.build(toBuild0)(if (monitor == null) null else new BuildProgressMonitor {
        def isCanceled = monitor.isCanceled
        def worked(howMuch : Int) = monitor.worked(howMuch)
      })
      toBuild0.foreach{file => toBuild += nscToLampion(file.asInstanceOf[PlainFile])}
      changed.map(file => nscToLampion(file.asInstanceOf[PlainFile]))
    }

    def stale(path : IPath) : Unit = {
      compiler.stale(path.toOSString)
      if (buildCompiler != null)
        buildCompiler.stale(path.toOSString)
    }
    
    def clean(implicit monitor : IProgressMonitor) = {
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

    import compiler.global._
    import org.eclipse.jdt.core.{IType,IJavaElement}
    import org.eclipse.core.runtime.IProgressMonitor
    protected def findJava(sym : compiler.Symbol) : Option[IJavaElement] = {
      if (sym == NoSymbol) None
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
      import org.eclipse.jdt.core.Signature._
      import compiler._
      import definitions._
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
      case tpe : PolyType if tpe.typeParams.length == 1 && tpe.resultType == ArrayClass.tpe => 
        "[" + signatureFor0(tpe.typeParams(0))
      case tpe : PolyType => signatureFor(tpe.resultType) 
      case tpe => signatureFor0(tpe.typeSymbol)  
      }
    }
    protected def classFileFor(sym : Symbol) : Option[IClassFile] = {
      findJava(sym).map{e => 
        var p = e
        while (p != null && !p.isInstanceOf[IClassFile]) p = p.getParent
        p.asInstanceOf[IClassFile]
      } match {
        case Some(null) => None
        case ret => ret
      }
    }
    private val classFiles = new LinkedHashMap[IClassFile,File] 
    def classFile(source : AbstractFile, classFile : IClassFile) = classFiles.get(classFile) match {
      case Some(file) => file
      case None => 
        val file = File(ClassFileSpec(source, classFile))
        classFiles(classFile) = file
        file
    }
    
    override def fileFor(sym : Symbol) : Option[Project#File] = sym.sourceFile match {
      case null => None
      case file : PlainFile => findFileFor(file)
      case source => classFileFor(sym.toplevelClass) match {
        case None => None 
        case Some(clazz) => Some(classFile(source,clazz))
      }
    }
    
    private def findFileFor(file : PlainFile) : Option[Project#File] = {
      import org.eclipse.core.runtime._
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

    private case class JavaRef(elem : IJavaElement, symbol0 : compiler.Symbol) extends IdeRef {
      override def hover = try {
        val str = elem.getAttachedJavadoc(null)
        if (str eq null) None
        else Some(str)
      } catch {
      case ex => 
        ScalaPlugin.this.logError(ex)
        Some("Method added to Java class by Scala compiler.")
      }
      import org.eclipse.jdt.ui._
      override def hyperlink =
        JavaUI.openInEditor(elem, true, true)
      override def symbol = Some(symbol0)
    }
    override protected def javaRef(symbol : compiler.Symbol) : IdeRef = {
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
      import scala.tools.nsc.io._
      import java.io
      val file = if (rootSource.endsWith(jarFileExtn)) {
        val jf = new io.File(rootSource)
        if (jf.exists && !jf.isDirectory) {
        val archive = ZipArchive.fromFile(jf)
        archive.lookupPath(fullSource,false)
      } else {
          logError("could not find jar file " + jf, null)
          return None
        } // xxxx.
      } else {
        val jf = new io.File(rootSource)
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
    Util.resetJavaLikeExtensions
    PlatformUI.getWorkbench.getEditorRegistry.setDefaultEditor("*.scala", editorId)
  }
  
  override def stop(context : BundleContext) = {
    ResourcesPlugin.getWorkspace.removeResourceChangeListener(this)

    super.stop(context)
  }
  
  override def resourceChanged(event : IResourceChangeEvent) {
    if(event.getType == IResourceChangeEvent.POST_CHANGE) {
      event.getDelta.accept(new IResourceDeltaVisitor {
        def visit(delta : IResourceDelta) : Boolean = {
          delta.getKind match {
            case IResourceDelta.CHANGED => {
              delta.getResource match {
                case f : IFile => {
                  if (ScalaPlugin.isScalaProject(f.getProject) &&
                    (JavaCore.create(f.getProject).isOnClasspath(f))) {
                      projectSafe(f.getProject).get.stale(f.getLocation)
                  }
                }
                case _ =>
              }
            }
            case _ =>
          }
          true
        }
      })
    }
    
    (event.getResource, event.getType) match {
      case (iproject : IProject, IResourceChangeEvent.PRE_CLOSE) => 
        val project = projects.removeKey(iproject)
        if (!project.isEmpty) project.get.destroy
      case _ =>
    }
  }
  
  def inputFor(that : AnyRef) : Option[IEditorInput] = that match {
  case that : IClassFile  => 
    scalaSourceFile(that).map{
    case (project,source) => new ClassFileInput(project,source,that)
    }
  case _ => None
  }
  
  import org.eclipse.jdt.internal.ui.javaeditor._
  import org.eclipse.jdt.internal.ui._
  class ClassFileInput(val project : Project, val source : AbstractFile, val classFile : IClassFile) extends InternalClassFileEditorInput(classFile) with FixedInput {
    assert(source != null)
    override def getAdapter(clazz : java.lang.Class[_]) = clazz match {
    case clazz if clazz == classOf[AbstractFile] => source
    case _ => super.getAdapter(clazz)  
    }
    override def initialize(doc : IDocument) : Unit = doc.set(new String(source.toCharArray))
    override def neutralFile = (project.classFile(source,classFile))
    override def createAnnotationModel = {
      (classFile.getAdapter(classOf[IResourceLocator]) match {
      case null => null
      case locator : IResourceLocator =>  locator.getContainingResource(classFile)
      }) match {
      case null => super.createAnnotationModel
      case resource =>
        val model = new ClassFileMarkerAnnotationModel(resource)
        model.setClassFile(classFile)
        model
      }
    }
  }
}
