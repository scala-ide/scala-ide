/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse;
import scala.tools.nsc._
import scala.tools.nsc.reporters._
import scala.tools.nsc.io._
import scala.collection.jcl._
import scala.tools.nsc.util._
import org.eclipse.core.runtime._
import org.eclipse.core.resources._
import org.eclipse.jdt.internal.core._
import java.nio.charset._
  
abstract class BuildCompiler extends Global(new Settings) with Compiler {
  def plugin : ScalaPlugin
  def project : ScalaPlugin#ProjectImpl
  private[eclipse] val javaDepends = new LinkedHashSet[IProject]
  override val loaders : symtab.SymbolLoaders { val global : BuildCompiler.this.type } = new symtab.SymbolLoaders {
    val global : BuildCompiler.this.type = BuildCompiler.this
    override def computeDepends(loader : PackageLoader) = BuildCompiler.this.computeDepends(loader.asInstanceOf[loaders.PackageLoader])
  }
  private val readers = new LinkedHashMap[String,SourceReader] {
    override def default(encoding : String) : SourceReader = {
      val charset =
        try {
          Charset.forName(encoding)
        } catch {
          case ex: IllegalCharsetNameException =>
            plugin.logError("illegal charset name '" + encoding + "'", ex)
            Charset.forName(scala.compat.Platform.defaultCharsetName)
          case ex: UnsupportedCharsetException =>
            plugin.logError("unsupported charset '" + encoding + "'", ex)
            Charset.forName(scala.compat.Platform.defaultCharsetName)
        }
      val ret = new SourceReader(charset.newDecoder(), BuildCompiler.this.reporter)
      this(encoding) = ret
      ret
    }
  } 
  
  // use workspace specified encoding for each file. 
  override def getSourceFile(f: AbstractFile): SourceFile = {
    // new BatchSourceFile(f, reader.read(f))
    val g = f match {
    case f : PlainFile => f
    case f => return super.getSourceFile(f)
    }
    val root = ResourcesPlugin.getWorkspace.getRoot
    val path = Path.fromOSString(g.file.getAbsolutePath)
    val file = root.getFile(path)
    val file0 = project.underlying.getFile(file.getProjectRelativePath)
    val charSet = file0.getCharset
    //Console.println("ENCODING0: " + file0 + " " + file0.getProject + " " + charSet + " " + file0.getProject.getDefaultCharset + " " + file0.getCharset(false))
    val reader = readers(charSet)
    //val reader = readers(file.getCharset)
    new BatchSourceFile(f, reader.read(f))
  }
  

  override def doPickleHash = true
  //import project._
  project.initialize(this)
  this.reporter = new Reporter {
    override def info0(pos : Position, msg : String, severity : Severity, force : Boolean) = (pos.offset,pos.source.map(_.file)) match {
    case (Some(offset),Some(file:PlainFile)) => 
      val source = pos.source.get
      import IMarker._
      val project = BuildCompiler.this.project
      severity.count += 1
      project.nscToLampion(file).buildError(severity match {
      case ERROR   => SEVERITY_ERROR
      case WARNING => SEVERITY_WARNING
      case INFO    => SEVERITY_INFO
      }, msg, offset, source.identifier(pos, BuildCompiler.this).getOrElse(" ").length)(null)
    case _ => 
      severity.count += 1
      System.err.println("XXX " + pos + " " + msg)  
      //assert(false)
    }
  }
  
  def build[File <: ScalaPlugin#ProjectImpl#FileImpl](toBuild : LinkedHashSet[File])(implicit monitor : IProgressMonitor) : List[File] = {
    // build all files, return what files have changed.
    val project = this.project
    val run = new Run {
      var worked : Int = 0
      override def progress(current : Int, total : Int) : Unit = {
        if (monitor != null && monitor.isCanceled) {
          cancel; return
        }
        assert(current <= total)
        val expected = (current:Double) / (total:Double)
        val worked0 = (expected * 100f).toInt
        assert(worked0 <= 100)
        if (worked0 > worked) {
          if (monitor != null) monitor.worked(worked0 - worked)
          worked = worked0
        }
      }
      override def compileLate(pfile : AbstractFile) = {
        super.compileLate(pfile)
        val file = project.nscToLampion(pfile.asInstanceOf[PlainFile]).asInstanceOf[File]
        if (toBuild add file) {
          assert(true)
          Console.println("late " + file)
          file.clearBuildErrors
        }
      }
    }
    val plugin = this.plugin
    val filenames = toBuild.projection.map(_.underlying match {
    case plugin.NormalFile(file) => file.getLocation.toOSString
    }).toList
    reporter.reset
    try {
      run.compile(filenames)
    } finally { 
      ()
    }
    val fldr = plugin.workspace.getFolder(project.javaProject.getOutputLocation)
    fldr.refreshLocal(IResource.DEPTH_INFINITE, null)
    // look at what is compiled
    var changed : List[File] = Nil
    run.units.foreach{unit => 
      val file = project.nscToLampion(unit.source.file.asInstanceOf[PlainFile]).asInstanceOf[File]
      toBuild += file // because it might not be there already.
      if (!file.hasBuildErrors) {
        ()
        val efile = file.underlying match {
        case plugin.NormalFile(file) => file
        }
        val ppath = efile.getParent.getLocation
        if (project.scalaDepends.contains(ppath)) {
          val depends = project.scalaDepends(ppath)
          val root = plugin.workspace.getLocation.toOSString
          def f(tree : Tree) : Unit = tree match {
          case PackageDef(_,body) => body.foreach(f)
          case tree@ ClassDef(_,_,_,_) if tree.symbol != NoSymbol => g(tree.symbol)
          case tree@ModuleDef(_,_,_)   if tree.symbol != NoSymbol => g(tree.symbol)
          case _ =>
          }
          def g(sym : Symbol) = {
            val name = sym.name.toString
            depends.removeKey(name) match {
            case None=>
            case Some(set) => set.foreach{path=>
                assert(path.toOSString.startsWith(root))
                val path0 = Path.fromOSString(path.toOSString.substring(root.length))
                val dpnd = plugin.workspace.getFile(path0)
                if (dpnd.exists) plugin.fileFor(dpnd) match {
                case None =>
                case Some(file) => 
                  assert(true)
                  changed = file.asInstanceOf[File] :: changed
                }
              }
            }
          }
          f(unit.body)
          depends.mirrors.foreach{mirror =>
            assert(true)
            val info = mirror.info
            if (info.isInstanceOf[PackageClassInfoType]) { // not now
              //mirror.info.asInstanceOf[PackageClassInfoType].lazyLoader.asInstanceOf[symtab.SymbolLoaders#PackageLoader].refresh
            }
          }
          
        }
        file.resetDependencies 
        if (file.signature != unit.pickleHash) {
          assert(true)
          file.signature = unit.pickleHash
          changed = file.asInstanceOf[File] :: changed
        }
        unit.depends.projection.map(_.sourceFile).filter(_ != null).foreach{
        case depend : PlainFile =>
          val root = plugin.workspace.getLocation.toOSString
          if (depend.path.startsWith(root)) {
            val path = Path.fromOSString(depend.path.substring(root.length))
            val dpnd = plugin.workspace.getFile(path)
            if (dpnd.exists) {
              file.dependsOn(dpnd.getFullPath)
              if (JavaProject.hasJavaNature(dpnd.getProject) && !dpnd.getProject.hasNature(plugin.natureId)) 
                javaDepends += dpnd.getProject
            } else logError("depend " + path + " does not exist!", null)
          }
        case _ => // in an archive, we can ignore it.  
        }
      }
    }
    run.symSource.foreach{ // what to do here?
    case (sym, source) => 
    }
    changed
  }
}
