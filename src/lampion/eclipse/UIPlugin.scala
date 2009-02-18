/*
 * Copyright 2005-2009 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.eclipse

import scala.collection.jcl._
import scala.xml.NodeSeq        

import java.io.{ ByteArrayInputStream, ByteArrayOutputStream, DataInputStream, DataOutputStream, ObjectInputStream, ObjectOutputStream }
import java.net.URL

import org.eclipse.core.resources.{IContainer,IFile,IFolder,IMarker,IProject,IResource,IResourceChangeEvent,IResourceChangeListener,IWorkspaceRunnable,ResourcesPlugin}
import org.eclipse.core.runtime.{FileLocator,IPath,IStatus,Path,Status,IProgressMonitor}
import org.eclipse.jdt.internal.ui.text.java.JavaCompletionProposal;
import org.eclipse.jface.dialogs.{ErrorDialog}
import org.eclipse.jface.resource.{ImageDescriptor}
import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.jface.text.{IRepairableDocument,TextPresentation,IDocument,ITextViewer,Document,Position,Region}
import org.eclipse.jface.text.hyperlink.{IHyperlink}
import org.eclipse.jface.text.contentassist.{ICompletionProposal}
import org.eclipse.jface.text.source.{Annotation,IAnnotationModel}
import org.eclipse.jface.text.source.projection.{ProjectionAnnotation,ProjectionViewer,ProjectionAnnotationModel}
import org.eclipse.swt.SWT
import org.eclipse.swt.graphics
import org.eclipse.swt.widgets.Display
import org.eclipse.swt.custom.StyleRange
import org.eclipse.ui.{ IEditorInput, IEditorReference, IFileEditorInput, IPathEditorInput, IPersistableElement, IWorkbenchPage, PlatformUI }
import org.eclipse.ui.ide.IDE
import org.eclipse.ui.plugin.AbstractUIPlugin
import org.eclipse.ui.texteditor.ITextEditor
import org.osgi.framework.BundleContext

import lampion.presentation.{Presentations}
import scala.tools.eclipse.Editor
import scala.tools.eclipse.SourceViewer

trait UIPlugin extends AbstractUIPlugin with IResourceChangeListener with lampion.core.Plugin with lampion.presentation.Matchers {
  def editorId : String = pluginId + ".Editor"
  type Color = graphics.Color
  val noColor : Color = null
  private[eclipse] def savePreferenceStore0 = savePreferenceStore

  type Fold = ProjectionAnnotation
  type ErrorAnnotation = Annotation
  type Image = graphics.Image
  type Annotation = org.eclipse.jface.text.source.Annotation
  type AnnotationKind = String
  
  type HighlightContext = TextPresentation
  
  def pluginId : String
  
  def workspace = ResourcesPlugin.getWorkspace.getRoot
  
  def problemMarkerId : Option[String] = None
  
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

  protected def Project(underlying : IProject) : Project
  
  type Project <: ProjectImpl
  
  trait ProjectImpl0 extends super[Plugin].ProjectImpl {
    def self : Project
    val underlying : IProject
    override def toString = underlying.getName
    override def isOpen = super.isOpen && underlying.isOpen  
    /* when a file needs to be rooted out */
    def stale(path : IPath) : Unit = {}
    def sourceFolders : Iterable[IContainer] = Nil
    def externalDepends : Iterable[IProject] = Nil
    def build(toBuild : LinkedHashSet[File])(implicit monitor : IProgressMonitor) : Seq[File] = toBuild.toList
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
    
    protected def File(underlying : FileSpec) : File
    type Path = IPath
    type File <: FileImpl
    trait FileImpl extends super.FileImpl {
      val underlying : FileSpec
      var dependencies = new LinkedHashSet[IPath]
      private var infoLoaded : Boolean = false
      def project0 : ProjectImpl0 = ProjectImpl0.this
      override def project : Project = ProjectImpl0.this.self
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
      
      def saveBuildInfo(output : DataOutputStream) : Unit = {
        val output0 = new ObjectOutputStream(output)
        output0.writeObject(dependencies.toList.map(_.toOSString))
      }
      def loadBuildInfo(input : DataInputStream) : Unit = {
        val input0 = new ObjectInputStream(input)
        val list = input0.readObject.asInstanceOf[List[String]]
        list.foreach(dependencies += Path.fromOSString(_))
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
    }
    private val files = new LinkedHashMap[IFile,File] {
      override def default(key : IFile) = {
        assert(key != null)
        val ret = File(NormalFile(key)); this(key) = ret; 
        val manager = ProjectImpl0.this.underlying.getFolder(".manager")
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
    def clean(implicit monitor : IProgressMonitor) : Unit = {
      if (!problemMarkerId.isEmpty)                
        underlying.deleteMarkers(problemMarkerId.get, true, IResource.DEPTH_INFINITE)
      doFullBuild = true
      val fldr =underlying.findMember(".manager")
      if (fldr != null && fldr.exists) {
        fldr.delete(true, monitor)
      }
    }
    /* private[eclipse] */ var doFullBuild = false
  }
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
  protected def canBeConverted(file : IFile) : Boolean = true
  protected def canBeConverted(project : IProject) : Boolean = true
  
  def projectSafe(project : IProject) = if (project eq null) None else projects.get(project) match {
  case _ if !project.exists() || !project.isOpen => None
  case None if canBeConverted(project) => Some(projects(project))
  case ret => ret
  }
  
  override def start(context : BundleContext) = {
    super.start(context)
    ResourcesPlugin.getWorkspace.addResourceChangeListener(this, IResourceChangeEvent.PRE_CLOSE | IResourceChangeEvent.POST_CHANGE)
  }
  override def stop(context : BundleContext) = {
    super.stop(context)
    ResourcesPlugin.getWorkspace.removeResourceChangeListener(this)
  }
  
  override def resourceChanged(event : IResourceChangeEvent) = (event.getResource, event.getType) match {
    case (iproject : IProject, IResourceChangeEvent.PRE_CLOSE) => 
      val project = projects.removeKey(iproject)
      if (!project.isEmpty) project.get.destroy
    case _ =>
  }
  /** error logging */
  override final def logError(msg : String, t : Throwable) : Unit = {
    var tt = t
    if (tt == null) tt = try {
      throw new Error
    } catch {
      case e : Error => e
    }
    val status = new Status(IStatus.ERROR, pluginId, IStatus.ERROR, msg, tt)
    log(status)
  }
  final def check[T](f : => T) = try { Some(f) } catch {
    case e : Throwable => logError(e); None
  }
  //protected def log(status : Status) = getLog.log(status)
  
  def getFile(path : IPath) : Option[File] = workspace.getFile(path) match {
  case file if file.exists =>
    projectSafe(file.getProject) match {
    case Some(project) => project.fileSafe(file)
    case None => None
    }
  case _ => None
  }
  def fileFor(file0 : IFile) : Option[File] = projectSafe(file0.getProject) match {
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
  
  /* private[eclipse] */ val viewers = new LinkedHashMap[ProjectImpl#FileImpl,SourceViewer]
 
  trait ProjectA extends ProjectImpl0
  trait ProjectB extends super[Matchers].ProjectImpl
  trait ProjectImpl extends ProjectA with ProjectB {
    def self : Project
    val ERROR_TYPE = "lampion.error"
    val MATCH_ERROR_TYPE = "lampion.error.match"

      
    override protected def checkAccess : checkAccess0.type= {
      val ret = super.checkAccess
      import org.eclipse.swt.widgets._
      assert(inUIThread || jobIsBusy)
      ret
    }
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

    def highlight(sv : SourceViewer, offset0 : Int, length0 : Int, style0 : Style)(implicit txt : TextPresentation) : Unit = {
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
      val style = style0.style0 // to perform parent overlays
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

    type File <: FileImpl
    trait FileImpl extends super[ProjectA].FileImpl with super[ProjectB].FileImpl {selfX : File => 
      def self : File
      def viewer : Option[SourceViewer] = viewers.get(self)
      def editor = viewer.map(_.editor) getOrElse None
      
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

      
      override def prepareForEditing = {
        super.prepareForEditing
        if (!viewer.isEmpty && viewer.get.projection != null) {
          val p = viewer.get.projection
          p.removeAllAnnotations
        }
      }
      override def highlight(offset0 : Int, length : Int, style : Style)(implicit txt : TextPresentation) : Unit = {
        val viewer = this.viewer
        if (viewer.isEmpty) return
        val sv = viewer.get
        ProjectImpl.this.highlight(sv, offset0, length, style)
      }
      override def invalidate(start : Int, end : Int)(implicit txt : PresentationContext) : Unit = {
        txt.invalidate.get(start) match {
        case Some(end0) =>
          if (end > end0) txt.invalidate(start) = end
        case None => txt.invalidate(start) = end
        }
      }
      override def invalidate0(start : Int, end : Int) : Unit = if (viewer.isDefined) {
        viewer.get.invalidateTextPresentation(start, end-start)
      }
      /* private[eclipse] */ def refresh(offset : Int, length : Int, pres : TextPresentation) = {
        refreshHighlightFor(offset, length)(pres)
      }
      private object content0 extends RandomAccessSeq[Char] {
        private def doc = viewer.get.getDocument
        def length = doc.getLength
        def apply(idx : Int) = doc.getChar(idx)
      }
      override def content : RandomAccessSeq[Char] = if (viewer.isDefined) content0 else 
        throw new Error(this + " not open for editing")
      override def createPresentationContext : PresentationContext = new PresentationContext
      override def finishPresentationContext(implicit txt : PresentationContext) : Unit = if (!viewer.isEmpty) {
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
            logError(ex)
        }
        finally {
          viewer.busy = oldBusy
        }
      }
      
      override def isLoaded = viewers.contains(self)
      def doLoad0(page : IWorkbenchPage) = underlying match {
        case NormalFile(underlying) => 
          IDE.openEditor(page, underlying, true)
      }
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
      override def isCollapsed(fold : ProjectionAnnotation) = fold.isCollapsed
      override def collapseRegion(fold : ProjectionAnnotation) = {
        val e = viewer.get.projection
        if (e == null) (0,0) else {
          val pos = e.getPosition(fold)
          if (pos == null) (0,0)        
          else (pos.offset,pos.offset + pos.length)
        }
      }
      override def destroyCollapseRegion(fold : ProjectionAnnotation)(implicit txt : PresentationContext)  = (viewer,txt) match {
      case (Some(viewer),null) =>
        val e = viewer.projection
        if (e != null) e.removeAnnotation(fold)
      case (_, null) => 
      case (_, txt) =>
        txt.remove = fold :: txt.remove
      }
      
      override def createCollapseRegion(from : Int, to : Int, old : Option[Fold])(implicit txt : PresentationContext) = old match {
      case Some(fold) =>
        val e = viewer.get.projection
        if (e != null) e.modifyAnnotationPosition(fold,new Position(from, to - from))
        fold
      case None => 
        val fold = new ProjectionAnnotation(false)
        txt.add(fold) = new Position(from,to - from)
        fold
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
  }
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
    def neutralFile : File
    def createAnnotationModel : IAnnotationModel = new ProjectionAnnotationModel
  }
  def fileFor(input : IEditorInput) : Option[File] = input match {
  case input : FixedInput => Some(input.neutralFile)
  case input : IFileEditorInput => fileFor(input.getFile)
  case _ => None
  }
  def Style(key : String) : StyleFactory = new StyleFactory {
    def style : Style = KeyStyle(key, this)
  }
  override lazy val noStyle = new Style {
    def underline = false
    def italics = false
    def strikeout = false
    def bold = false
    def background = noColor
    def foreground = noColor
  }
  
  abstract class Style extends StyleImpl {
    def self = this
    private[UIPlugin] def style0 : Style = this
    override def overlay0(style0 : Style) = {
      val original = Style.this
      val style = style0.style0
      new Style {
        def underline = original.underline || style.underline
        def italics = original.italics || style.italics
        def bold = original.bold || style.bold
        def strikeout = original.strikeout || style.strikeout
        def foreground = {
          if (original.foreground == noColor) 
            style.foreground else original.foreground
        }
        def background = if (original.background == noColor) style.background else original.background
      }
    }
  } 
  case class KeyStyle(key : String, defaults : StyleFactory) extends Style with scala.tools.eclipse.properties.EditorPreferences.Key {
    private[UIPlugin] override def style0 = defaults.parent match {
      case Some(parent) => load; overlay(parent.style0)
      case _ => load; super.style0
    }
    def default(appendix : String) = appendix match {
    case `foregroundId` => defaults.foreground
    case `backgroundId` => defaults.background
    case `boldId` => defaults.bold0
    case `italicsId` => defaults.italics0
    case `underlineId` => defaults.underline0
    case `strikeoutId` => defaults.strikeout0
    }

    override def overlay0(style : Style) = defaults.parent match {
    case Some(parent) => super.overlay0(parent.overlay(style))
    case _ => super.overlay0(style)
    }
    override def styleKey = pluginId + "." + key
    
    def refresh : Unit = {
      loaded = false
      children.foreach(_.refresh)
    }
    var foreground0 : Color = defaults.foreground
    var background0 : Color = defaults.background
    var bold0 : Boolean = defaults.bold0
    var italics0 : Boolean = defaults.italics0
    var underline0 : Boolean = defaults.underline0
    var strikeout0 : Boolean = defaults.strikeout0
    
    override def foreground = foreground0 
    override def background = background0
    override def bold = bold0
    override def italics = italics0
    override def underline = underline0
    override def strikeout = strikeout0
    private var loaded = false
    def load : Boolean = if (!loaded) {
      loaded = true
      if (!defaults.parent.isEmpty) defaults.parent.get.asInstanceOf[KeyStyle].load
      val store = editorPreferenceStore
      def color(what : String) : Color = {
	val key = styleKey + what
        store
        if (store.isDefault(key)) {
          val ret = default(what).asInstanceOf[Color]
          if (ret == null) {
            store.setValue(key,-1)
            null
          } else {
            PreferenceConverter.setValue(store,key,ret.getRGB)
            ret
          }
        } else (PreferenceConverter.getColor(store, key))
      }
      def boolean(what : String) = {
        val key = styleKey + what
        /*if (store.isDefault(key)) {
          val ret = default(what).asInstanceOf[Boolean]
          store.setValue(key, ret)
          ret
        }
        else*/
        store.getBoolean(key)
      }
      foreground0 = color(foregroundId)
      background0 = color(backgroundId)
      bold0 = boolean(boldId)
      underline0 = boolean(underlineId)
      italics0 = boolean(italicsId)
      strikeout0 = boolean(strikeoutId)
      true
    } else true
    private[eclipse] val children = new scala.collection.jcl.LinkedHashSet[KeyStyle]
    defaults.parent.foreach(_.asInstanceOf[KeyStyle].children += this)
    preferences.styles += this
    preferences.editorPreferences += this
  }
  val foregroundId = ".Color.Foreground"
  val backgroundId = ".Color.Background"
  val boldId    = ".Bold"
  val italicsId = ".Italics"
  val underlineId = ".Underline"
  val strikeoutId = ".Strikeout"
    
  /* private[eclipse] */ object colorMap extends scala.collection.jcl.LinkedHashMap[graphics.RGB,graphics.Color] {
    override def apply(rgb : graphics.RGB) : graphics.Color = rgb match {
    case rgb if rgb == PreferenceConverter.COLOR_DEFAULT_DEFAULT => null
    case rgb => super.apply(rgb)
    }
    override def default(rgb : graphics.RGB) = {
      val ret = new graphics.Color(Display.getDefault(), rgb)
      this(rgb) = ret
      ret
    }
  }
  def rgb(r : Int, g : Int, b : Int) = colorMap(new graphics.RGB(r,g,b))
  
  /* private[eclipse] */ implicit def rgb2color(rgb : graphics.RGB) = colorMap(rgb)
  /* private[eclipse] */ def initializeEditorPreferences = {
    val store = editorPreferenceStore
    preferences.styles.foreach{style =>
    store.setValue(style.styleKey + boldId, style.defaults.bold0)
    store.setValue(style.styleKey + italicsId, style.defaults.italics0)
    store.setValue(style.styleKey + underlineId, style.defaults.underline0)
    store.setValue(style.styleKey + strikeoutId, style.defaults.strikeout0)
    PreferenceConverter.setValue(store, style.styleKey + foregroundId, style.defaults.foreground.getRGB)
    PreferenceConverter.setValue(store, style.styleKey + backgroundId, style.defaults.background.getRGB)
    }
  }
  
  
  override def initializeDefaultPreferences(store0 : org.eclipse.jface.preference.IPreferenceStore) = {
    super.initializeDefaultPreferences(store0)
    initializeEditorPreferences
  }
  /* private[eclipse] */ object preferences {
    val editorPreferences = new ArrayList[scala.tools.eclipse.properties.EditorPreferences.Key]
    val styles = new LinkedHashSet[KeyStyle]
  }
  
  //preferences.editorPreferences += editorKey

  private val images = new LinkedHashMap[ImageDescriptor,Image]
  def image(desc : ImageDescriptor) = images.get(desc) match {
  case Some(image) => image
  case None => 
    val image = desc.createImage
    images(desc) = image; image
  }
  def fullIcon(name : String) = 
    image(ImageDescriptor.createFromURL(new URL(getBundle.getEntry("/icons/full/"), name)))
    
  def editorPreferenceStore = 
    org.eclipse.ui.editors.text.EditorsUI.getPreferenceStore
  def bundle : java.util.ResourceBundle = MyBundle
  protected object MyBundle extends java.util.ResourceBundle {
    def getKeys = new java.util.Enumeration[String] {
      def nextElement = throw new Error;
      def hasMoreElements = false;
    }
    def handleGetObject(str : String) : Object = throw new java.util.MissingResourceException("","","");
  }
  
}
