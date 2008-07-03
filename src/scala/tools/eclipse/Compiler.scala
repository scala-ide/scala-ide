/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse;
import scala.tools.nsc._
import scala.collection.jcl.{LinkedHashMap,LinkedHashSet}
import scala.tools.nsc.io.{AbstractFile,PlainFile}


trait CompilerProject {
  def charSet(file : PlainFile) : String
  def initialize(compiler : Compiler) : Unit
  def logError(msg : String, e : Throwable) : Unit
  val scalaDepends = new LinkedHashMap[String,ScalaDependMap] {
    override def default(key : String) = {
      val ret = new ScalaDependMap
      this(key) = ret; ret
    }
  } 
  //def workspacePath : String
  def projectFor(path : String) : Option[CompilerProject]
  def fileFor(path : String) : PlainFile // relative to workspace
  def signature(file : PlainFile) : Long
  def setSignature(file : PlainFile, value : Long) : Unit
  def refreshOutput : Unit
  def resetDependencies(file : PlainFile) : Unit
  def dependsOn(file : PlainFile, what : PlainFile) : Unit
  
  def buildError(file : PlainFile, severity0 : Int, msg : String, offset : Int, identifier : Int) : Unit
  def clearBuildErrors(file : AbstractFile) : Unit  
  def hasBuildErrors(file : PlainFile) : Boolean
  
  class ScalaDependMap extends LinkedHashMap[String,LinkedHashSet[String]] {
    val mirrors = new LinkedHashSet[Global#Symbol] 
    def asDependMap(compiler : Global, pkg : Global#Symbol) : Global#PackageScopeDependMap = {
      mirrors += pkg
      return new compiler.PackageScopeDependMap {
        def createDepend(sym : compiler.Symbol, name : compiler.Name) = {
          val top = sym.toplevelClass.sourceFile
          top match {
            case top : PlainFile => 
              val path = top.file.getAbsolutePath
              //assert(path0.startsWith(workspacePath))
              //val path = path0.substring(workspacePath.length)
              ScalaDependMap.this.apply(name.toString) += path
            case _ =>
          }
        }
      } 
    }
    override def default(key : String) = {
      val ret = new LinkedHashSet[String]
      this(key) = ret; ret
    }
  }
}
trait Compiler extends Global {  
  def project : CompilerProject

  private val reload = new LinkedHashMap[String,LinkedHashMap[Symbol,LazyType]] {
    override def default(path : String) = {
      val ret = new LinkedHashMap[Symbol,LazyType]
      this(path) = ret; ret
    }
  }

  def computeDepends(loader : loaders.PackageLoader) = {
    if (loader.directory.entries.isEmpty || 
        loader.directory.entries.head.source == null ||
        !loader.directory.entries.head.source.location.isInstanceOf[PlainFile]) null else {
      val path = loader.directory.entries.head.source.location.toString;
      { //if (!path.startsWith(project.workspacePath)) null else {
        //assert(path != project.workspacePath)
        //val path1 = path.substring(project.workspacePath.length)
        val otherProject = project.projectFor(path)
        if (!otherProject.isDefined || otherProject.get == project) null else {
          val ret = otherProject.get.scalaDepends(path).asDependMap(Compiler.this,loader.root).asInstanceOf[PackageScopeDependMap]
          ret
        }
      }
    }
  }
  override def prepareReset(clazz : Symbol, tpe : LazyType) = {
    super.prepareReset(clazz, tpe) 
    val sourceFile = clazz.sourceFile
    sourceFile match {
    case sourceFile : PlainFile => 
      //val workspace = project.workspacePath
      //if (sourceFile.path.startsWith(workspace)) 
      {
        //val path = sourceFile.path.substring(workspace.length)
        //val file = plugin.workspace.getFile(Path.fromOSString(path))
        //assert(!clazz.isModuleClass)
        //assert(file.exists)
        //reload(file.getFullPath.toOSString)(clazz) = (tpe)
      }
      reload(sourceFile.path)(clazz) = (tpe)
    case _ => 
    }
  }
  // should not include workspace path
  def check(b : Boolean) = {
    assert(b)
  }
  
  def stale(path : String) : Seq[Symbol] = {
    //assert(!path.startsWith(project.workspacePath))
    reload.removeKey(path) match {
  case Some(set) => 
    var ret = List[Symbol]()
    set.foreach{case (sym,tpe) =>
      assert(sym.isModule || sym.isClass)
      assert(!sym.isModuleClass)
      val module = if (sym.isModule) sym else sym.linkedModuleOfClass
      val clazz = if (sym.isClass) sym else sym.linkedClassOfModule
      // both are here. 
      if (module ne NoSymbol) {
        val moduleClass = module.moduleClass
        assert(moduleClass ne NoSymbol)
        assert(moduleClass.sourceModule eq module)
        module.setInfo(tpe)
        moduleClass.setInfo(loaders.moduleClassLoader.asInstanceOf[Type])
        ret = module :: ret
      }
      if (clazz ne NoSymbol) {
        clazz.setInfo(tpe)
        ret = clazz :: ret
      }
    }
    ret
  case None => Nil
  }}      
}
