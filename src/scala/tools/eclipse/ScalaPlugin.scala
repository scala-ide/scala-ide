/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse
import scala.tools.nsc._
import org.eclipse.jdt.core._
import org.eclipse.jdt.internal.core._
import org.eclipse.core.runtime._
import org.eclipse.core.resources._
import scala.collection.jcl.{LinkedHashMap,LinkedHashSet}
import scala.tools.nsc.io.{AbstractFile,PlainFile,ZipArchive}

object ScalaPlugin { 
  private[eclipse] var plugin : ScalaPlugin = _
}

/** stuff needed to get the non-UI parts of the plugin going */
trait ScalaPluginSuperA extends lampion.eclipse.Plugin
trait ScalaPlugin extends ScalaPluginSuperA with scala.tools.editor.Driver {
  assert(ScalaPlugin.plugin == null)
  ScalaPlugin.plugin = this
  
  override def pluginId = "ch.epfl.lamp.sdt.core"
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
  def msilNatureId = pluginId + ".scalamsilnature"  
  def launchId = "ch.epfl.lamp.sdt.launching"
  val scalaLib = "SCALA_CONTAINER"
  val scalaHome = "SCALA_HOME"
  def scalaLibId  = launchId + "." + scalaLib
  def scalaHomeId = launchId + "." + scalaHome
  def launchTypeId = "scala.application"
  override def problemMarkerId = Some(pluginId + ".marker")

  def sourceFolders(javaProject : IJavaProject) : Iterable[IFolder] = {
    val isOpen = javaProject.isOpen
    if (!isOpen) javaProject.open(null)
    val isConsistent = javaProject.isConsistent
    val x0 = javaProject.isStructureKnown
    val x1 = javaProject.isReadOnly
    javaProject.getAllPackageFragmentRoots.filter(p => {
      check(p.getKind == IPackageFragmentRoot.K_SOURCE && p.getResource.isInstanceOf[IFolder] && {
        val parent = p.getParent
        val project = parent.getAdapter(classOf[IProject])
        val jp = javaProject.getProject
        parent == javaProject
      }) getOrElse false
    }).map(_.getResource.asInstanceOf[IFolder])
  }
  def javaProject(p : IProject) = 
    if (JavaProject.hasJavaNature(p)) Some(JavaCore.create(p))
    else None
    
  def resolve(path : IPath) : IPath = {
    assert(path != null)
    import path.lastSegment
    if (lastSegment == null) return path
    val res = if (lastSegment.endsWith(".jar") || lastSegment.endsWith(".zip"))
      workspace.getFile(path)
    else workspace.getFolder(path)
    assert(res != null)
    if (res.exists) res.getLocation else path
  }
  protected case class ClassFileSpec(source : AbstractFile, classFile : IClassFile) extends FileSpec {
    override def toString = source.name
    override def path = None // because they can't change or be recompiled.
  }
  type Project <: ProjectImpl
  trait ProjectA extends super[ScalaPluginSuperA].ProjectImpl
  trait ProjectB extends super[Driver].ProjectImpl
  trait ProjectImpl extends ProjectA with ProjectB {
    override def externalDepends = 
      if (buildCompiler eq null) Nil
      else buildCompiler.javaDepends
      

    def self : Project
    assert(underlying != null) // already initialized, I hope!
    assert(underlying.hasNature(natureId))
    assert(JavaProject.hasJavaNature(underlying))
    def javaProject = JavaCore.create(underlying)
    override def sourceFolders = ScalaPlugin.this.sourceFolders(javaProject)
    
    def outputPath = outputPath0.toOSString
    def outputPath0 = check {
      val fldr = workspace.getFolder(javaProject.getOutputLocation)
      if (!fldr.exists()) {
        fldr.create(true, true, null)
        fldr.setDerived(true)
      }
      fldr.getLocation
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
    object compiler0 extends nsc.Global(new Settings(null), new CompilerReporter) with super.Compiler with eclipse.Compiler {
      def plugin = ScalaPlugin.this
      override def logError(msg : String, t : Throwable) =
        ScalaPlugin.this.logError(msg, t)
      override def stale(path : IPath) : Seq[Symbol] = {
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
      initialize(this)
    }
    lazy val compiler : compiler0.type = compiler0
    import java.io.File.pathSeparator 
    
    private implicit def r2o[T <: AnyRef](x : T) = if (x == null) None else Some(x)
    def initialize(global : nsc.Global) = {
      val settings = new Settings(null)
      val sourceFolders = this.sourceFolders
      val sourcePath = sourceFolders.map(_.getLocation.toOSString).mkString("", pathSeparator, "")
      settings.sourcepath.tryToSet("-sourcepath" :: sourcePath :: Nil)
      settings.outdir.tryToSet("-d" :: outputPath :: Nil)
      settings.classpath.tryToSet("-classpath" :: "" :: Nil)
      settings.bootclasspath.tryToSet("-bootclasspath" :: "" :: Nil)
      if (!sourceFolders.isEmpty) {
        settings.encoding.value = sourceFolders.elements.next.getDefaultCharset
      }
      settings.deprecation.value = true
      settings.unchecked.value = true
      settings.allSettings.elements.filter(!_.hiddenToIDE).map{
      case setting => 
        (setting,underlying.getPersistentProperty(new QualifiedName(pluginId, setting.name.substring(1))))      
      }.filter(_._2 != null).foreach{
      case (setting,value) => 
        assert(true)  
        setting.tryToSet(setting.name :: value :: Nil)
      }
      global.settings = settings
      global.classPath.entries.clear
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
    type File <: FileImpl
    trait FileImpl extends super[ProjectA].FileImpl with super[ProjectB].FileImpl {selfX:File=>
      def self : File
      private[eclipse] var signature : Long = 0
      import java.io._
      
      override def nscFile : AbstractFile = file.underlying match {
      case NormalFile(file) => new PlainFile(file.getLocation.toFile)
      case ClassFileSpec(source,clazz) => source
      }
      
      override def saveBuildInfo(output : DataOutputStream) : Unit = {
        super.saveBuildInfo(output)
        output.writeLong(signature)
      }
      override def loadBuildInfo(input : DataInputStream) : Unit = {
        super.loadBuildInfo(input)
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
        assert(true)
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
        assert(true)
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
      
      
    }
    import scala.tools.nsc.io.{AbstractFile,PlainFile}

    def nscToLampion(file : PlainFile) : File = {
      val projectPath = underlying.getLocation.toOSString
      assert(file.path.startsWith(projectPath))
      val path = Path.fromOSString(file.path.substring(projectPath.length))
      val file0 = underlying.getFile(path)
      val file1 = fileSafe(file0).get
      file1
    } 
    class BuildCompiler extends eclipse.BuildCompiler {
      override def plugin : ScalaPlugin = ScalaPlugin.this
      override val project : ProjectImpl.this.type = ProjectImpl.this
    }
    private var buildCompiler : BuildCompiler = _
    override def build(toBuild : LinkedHashSet[File])(implicit monitor : IProgressMonitor) : Seq[File] = {
      checkClasspath
      if (buildCompiler == null) {
        buildCompiler = new BuildCompiler // causes it to initialize.
      }
      buildCompiler.build(toBuild)
    }
    // XXX: what about the presentation compiler?????
    override def stale(path : IPath) : Unit = {
      super.stale(path)
      compiler.stale(path)
      if (buildCompiler != null) {
        assert(true) 
        buildCompiler.stale(path)
      }
    } 
    override def clean(implicit monitor : IProgressMonitor) = {
      super.clean
      buildCompiler = null // throw out the compiler.
      // delete the class files in bin
      def delete(fldr : IFolder)(f : String => Boolean) : Unit = {
        if (!fldr.exists()) return
        fldr.members.foreach{
        case fldr : IFolder => try {
          fldr.delete(true, monitor) // might not work.
        } catch {
          case t => delete(fldr)(f)
        }
        case file : IFile if f(file.getName) => try {
          file.delete(true, monitor)
        } catch {
          case t => logError(t)
        }
        case _ => 
        }
      }
      delete(workspace.getFolder(javaProject.getOutputLocation))(_.endsWith(".class"))
    }
    import compiler.global._
    import org.eclipse.jdt.core.{IType,IJavaElement}
    import org.eclipse.core.runtime.IProgressMonitor
    protected def findJava(sym : compiler.Symbol) : Option[IJavaElement] = {
      if (sym == NoSymbol) None
      else if (sym.owner.isPackageClass) {
        val found = javaProject.findType(sym.owner.fullNameString('.'), sym.simpleName.toString, null : IProgressMonitor)
        if (found eq null) None else Some(found)
      } else {
        findJava(sym.owner) match {
          case Some(owner : IType) =>
            var ret : Option[IJavaElement] = None
            implicit def coerce(c : IJavaElement) : Option[IJavaElement] = if (c eq null) None else Some(c)
            if (ret.isEmpty && sym.isMethod) {
              val params = sym.info.paramTypes.map(signatureFor).toArray
              ret = owner.getMethod(sym.nameString, params)
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
    
    override def fileFor(sym : Symbol) : Option[ScalaPlugin.this.File] = sym.sourceFile match {
    case null => None
    case file : PlainFile => findFileFor(file)
    case source => classFileFor(sym.toplevelClass) match {
      case None => None 
      case Some(clazz) => Some(classFile(source,clazz))
      }
    }
    private def findFileFor(file : PlainFile) : Option[ScalaPlugin.this.File] = {
      import org.eclipse.core.runtime._
      var path = Path.fromOSString(file.path)
      val loc = workspace.getLocation
      if (!loc.isPrefixOf(path)) return None // not even in the workspace
      path = path.removeFirstSegments(loc.segmentCount)
      val file0 = workspace.getFile(path)
      assert(file0.exists)
      val project = projectSafe(file0.getProject)
      if (project.isEmpty) return None // not in a valid project.
      path = file0.getProjectRelativePath
      val fldr = project.get.sourceFolders.find(_.getProjectRelativePath.isPrefixOf(path))
      if (fldr.isEmpty) return None 
      val project0 = project.get
      return project0.fileSafe(file0)
    }

  }
  override protected def canBeConverted(file : IFile) : Boolean = 
    super.canBeConverted(file) && file.getName.endsWith(".scala")
  override protected def canBeConverted(project : IProject) : Boolean = 
    super.canBeConverted(project) && project.hasNature(natureId)
  
  private[eclipse] def scalaSourceFile(classFile : IClassFile) : Option[(Project,AbstractFile)] = {
    val source = classFile.getType.asInstanceOf[BinaryType].getSourceFileName(null)
    val project = projectSafe(classFile.getJavaProject.getProject)
    if (source != null && source.endsWith(".scala") && project.isDefined) {
      val pkgFrag = classFile.getType.getPackageFragment.asInstanceOf[PackageFragment]
      val rootSource = pkgFrag.getPackageFragmentRoot.getSourceAttachmentPath.toOSString
      val fullSource = pkgFrag.names.mkString("", "" + java.io.File.separatorChar, "") + java.io.File.separatorChar + source
      import scala.tools.nsc.io._
      import java.io
      val file = if (rootSource.endsWith(".jar")) {
        val jf = new io.File(rootSource)
        assert(jf.exists && !jf.isDirectory)
        val archive = ZipArchive.fromFile(jf)
        archive.lookupPath(fullSource,false)
      } else {
        val jf = new io.File(rootSource)
        assert(jf.exists && jf.isDirectory)
        val dir = PlainFile.fromFile(jf)
        dir.lookupPath(fullSource, false)
      }
      Some(project.get, file)
    } else None
  }
}
