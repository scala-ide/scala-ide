/*
 * Copyright 2005-2008 LAMP/EPFL
 * @author Sean McDirmid
 */
// $Id$

package scala.tools.eclipse;
import scala.tools.nsc._
import org.eclipse.jdt.core._
import org.eclipse.jdt.internal.core._
import org.eclipse.core.runtime._
import org.eclipse.core.resources._
import scala.collection.jcl.{LinkedHashMap,LinkedHashSet}
import scala.tools.nsc.io.{AbstractFile,PlainFile}

trait Compiler extends Global {
  def plugin : ScalaPlugin 
  private val reload = new LinkedHashMap[IPath,(Symbol,LazyType)]
  override def prepareReset(clazz : Symbol, tpe : LazyType) = {
    super.prepareReset(clazz, tpe) 
    val sourceFile = clazz.sourceFile
    sourceFile match {
    case sourceFile : PlainFile => 
      val workspace = plugin.workspace.getLocation.toOSString
      if (sourceFile.path.startsWith(workspace)) {
        val path = sourceFile.path.substring(workspace.length)
        val file = plugin.workspace.getFile(Path.fromOSString(path))
        assert(!clazz.isModuleClass)
        assert(file.exists)
        reload(file.getFullPath) = (clazz,tpe)
      }
    case _ => 
    }
  }
  def stale(path : IPath) : Seq[Symbol] = reload.removeKey(path) match {
    case Some((sym,tpe)) =>
      assert(sym.isModule || sym.isClass)
      assert(!sym.isModuleClass)
      val module = if (sym.isModule) sym else sym.linkedModuleOfClass
      val clazz = if (sym.isClass) sym else sym.linkedClassOfModule
      // both are here. 
      var ret = List[Symbol]()
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
      ret
    case None => Nil
  }      
}
