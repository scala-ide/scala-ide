/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package lampion.eclipse;
import org.eclipse.core.runtime
import runtime._
import org.eclipse.core.resources._
import scala.collection.jcl.{LinkedHashMap,LinkedHashSet}
import org.osgi.framework.BundleContext
import java.io._
 
trait Plugin extends runtime.Plugin with IResourceChangeListener with lampion.core.Plugin {
  // add more stuff later
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
  trait ProjectImpl extends super.ProjectImpl {
    def self : Project
    val underlying : IProject
    override def toString = underlying.getName
    override def isOpen = super.isOpen && underlying.isOpen  
    /* when a file needs to be rooted out */
    def stale(path : IPath) : Unit = {}
    def sourceFolders : Iterable[IFolder] = Nil
    def externalDepends : Iterable[IProject] = Nil
    def build(toBuild : LinkedHashSet[File])(implicit monitor : IProgressMonitor) : Seq[File] = toBuild.toList
    def buildDone(built : LinkedHashSet[File])(implicit monitor : IProgressMonitor) : Unit = if (!built.isEmpty) {
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
    protected def File(underlying : FileSpec) : File
    type Path = IPath
    type File <: FileImpl
    trait FileImpl extends super.FileImpl {
      val underlying : FileSpec
      var dependencies = new LinkedHashSet[IPath]
      private var infoLoaded : Boolean = false
      def project0 : ProjectImpl = ProjectImpl.this
      override def project : Project = ProjectImpl.this.self
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
        var result = false
        result = !file.findMarkers(problemMarkerId.get, true, IResource.DEPTH_INFINITE).isEmpty
        result
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
        val manager = ProjectImpl.this.underlying.getFolder(".manager")
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
    private[eclipse] var doFullBuild = false
  }
  class DependMap extends LinkedHashMap[IPath,LinkedHashSet[IPath]] {
    override def default(key : IPath) = {
      val ret = new LinkedHashSet[IPath]
      this(key) = ret; ret
    }
  }
  private[eclipse] object reverseDependencies extends DependMap
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
  protected def log(status : Status) = getLog.log(status)
  
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
}
