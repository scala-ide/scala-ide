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
//import org.eclipse.core.runtime._
//import org.eclipse.core.resources._
//import org.eclipse.jdt.internal.core._
import java.nio.charset._ 
   
trait BuildProgressMonitor {
  def isCanceled : Boolean
  def worked(howMuch : Int) : Unit
}

class BuildCompiler(val project : CompilerProject) extends Global(new Settings) with Compiler {
  //def plugin : ScalaPlugin
  
  //private[eclipse] val javaDepends = new LinkedHashSet[JavaProject]
  override lazy val loaders : symtab.SymbolLoaders { val global : BuildCompiler.this.type } = new symtab.SymbolLoaders {
    lazy val global : BuildCompiler.this.type = BuildCompiler.this
    override def computeDepends(loader : PackageLoader) = BuildCompiler.this.computeDepends(loader.asInstanceOf[loaders.PackageLoader])
  }
  private val readers = new LinkedHashMap[String,SourceReader] {
    override def default(encoding : String) : SourceReader = {
      val charset =
        try {
          Charset.forName(encoding)
        } catch {
          case ex: IllegalCharsetNameException =>
            project.logError("illegal charset name '" + encoding + "'", ex)
            Charset.forName(scala.compat.Platform.defaultCharsetName)
          case ex: UnsupportedCharsetException =>
            project.logError("unsupported charset '" + encoding + "'", ex)
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
    //assert(g.file.getAbsolutePath.startsWith(project.workspacePath))
    //val file = g.file.getAbsolutePath.substring(project.workspacePath.length)
    
    //val root = ResourcesPlugin.getWorkspace.getRoot
    //val path = Path.fromOSString(g.file.getAbsolutePath)
    //val file = root.getFile(path)
    //val file0 = project.underlying.getFile(file.getProjectRelativePath)
    val charSet = project.charSet(g) // .getCharset
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
      //import IMarker._
      val project = BuildCompiler.this.project
      severity.count += 1
      file
      project.buildError(file, severity.id, msg, offset, source.identifier(pos, BuildCompiler.this).getOrElse(" ").length)
      
/*      project.nscToLampion(file).buildError(severity match {
      case ERROR   => SEVERITY_ERROR
      case WARNING => SEVERITY_WARNING
      case INFO    => SEVERITY_INFO
      }, msg, offset, source.identifier(pos, BuildCompiler.this).getOrElse(" ").length)(null) */
    case _ => 
      severity.count += 1
      System.err.println("XXX " + pos + " " + msg)  
      //assert(false)
    }
  }
  
  def build(toBuild : LinkedHashSet[AbstractFile])(implicit monitor : BuildProgressMonitor) : List[AbstractFile] = {
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
        //val file = project.nscToLampion(pfile.asInstanceOf[PlainFile]).asInstanceOf[File]
        if (toBuild add pfile) {
          assert(true)
          Console.println("late " + pfile)
          project.clearBuildErrors(pfile) // .clearBuildErrors
        }
      }
    }
    //val plugin = this.plugin
    val filenames = toBuild.map(_.file.getAbsolutePath).toList
    reporter.reset
    try {
      run.compile(filenames)
    } finally {
      ()
    }
    project.refreshOutput
    //val fldr = plugin.workspace.getFolder(project.javaProject.getOutputLocation)
    //fldr.refreshLocal(IResource.DEPTH_INFINITE, null)
    // look at what is compiled
    var changed : List[AbstractFile] = Nil
    run.units.foreach{unit => 
      //val file = project.nscToLampion(unit.source.file.asInstanceOf[PlainFile]).asInstanceOf[File]
      val file = unit.source.file.asInstanceOf[PlainFile]
      toBuild += file // because it might not be there already.
      if (!project.hasBuildErrors(file)) {
        ()
        val ppath = file.file.getParentFile.getAbsolutePath
        if (project.scalaDepends.contains(ppath)) {
          val depends = project.scalaDepends(ppath)
          //val root = project.workspacePath // plugin.workspace.getLocation.toOSString
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
              //assert(!path.startsWith(root))
              //val path0 = Path.fromOSString(path.toOSString.substring(root.length))
              //val dpnd = plugin.workspace.getFile(path0)
              //val dpnd = project.fileFor(path)
              //changed = dpnd :: changed
              /*if (dpnd.file.exists) plugin.fileFor(dpnd) match {
                case None =>
                case Some(file) => 
                  assert(true)
                  changed = file.asInstanceOf[File] :: changed
                }
              }*/
            }}
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
        project.resetDependencies(file)
        if (project.signature(file) != unit.pickleHash) {
          assert(true)
          project.setSignature(file, unit.pickleHash)
          changed = file :: changed
        }
        unit.depends.projection.map(_.sourceFile).filter(_ != null).foreach{
        case depend : PlainFile =>
          //val root = plugin.workspace.getLocation.toOSString
          //val root = project.workspacePath
          { //if (depend.path.startsWith(root)) {
            project.dependsOn(file, depend)
            /*
            val path = Path.fromOSString(depend.path.substring(root.length))
            val dpnd = plugin.workspace.getFile(path)
            if (dpnd.exists) {
              file.dependsOn(dpnd.getFullPath)
              if (JavaProject.hasJavaNature(dpnd.getProject) && !dpnd.getProject.hasNature(plugin.natureId)) 
                javaDepends += dpnd.getProject
            } else logError("depend " + path + " does not exist!", null)
            */
          }
        case _ => // in an archive, we can ignore it.  }
        }
      }
    }
    run.symSource.foreach{ // what to do here?
    case (sym, source) => 
    }
    changed
  }
}
