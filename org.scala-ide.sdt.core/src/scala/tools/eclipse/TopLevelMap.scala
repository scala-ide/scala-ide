/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.collection.mutable.HashMap

import org.eclipse.core.resources.IFile

import scala.tools.nsc.{ CompilerCommand, FatalError, Global, NoPhase, Settings }
import scala.tools.nsc.ast.parser.Tokens
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.{ ConsoleReporter, Reporter }

import scala.tools.eclipse.util.EclipseFile

class TopLevelMap {
  val settings = new Settings(error)
  settings.classpath.value = ScalaPlugin.plugin.libClasses.get.toOSString
  val reporter = new ConsoleReporter(settings)
  val command = new CompilerCommand(Nil, settings)
  val compiler = new Global(command.settings, reporter)
  
  val fileToTypes = new HashMap[IFile, List[String]]
  val typeToFile = new HashMap[String, IFile]
  
  def sourceFileFor(qualifiedName : String) = typeToFile.get(qualifiedName)
  
  def parse(files : List[IFile]) {
    import compiler.{ ClassDef, CompilationUnit, GlobalPhase, ModuleDef, nme, PackageDef, Traverser, Tree }
    import compiler.syntaxAnalyzer.UnitParser
    
    val run = new compiler.Run
    compiler.phase = run.phaseNamed("parser")
    for (file <- files) {
      val unit = new CompilationUnit(compiler.getSourceFile(new EclipseFile(file)))
      val parser = new UnitParser(unit)
      val tree = parser.parse
      
      object extractor extends Traverser {
        var packagePrefix = ""
        def addMapping(qualifiedName : String) {
          typeToFile(qualifiedName) = file
          fileToTypes(file) = fileToTypes.get(file) match {
            case Some(types) => qualifiedName :: types
            case None => List(qualifiedName)
          }
        }
          
        override def traverse(tree: Tree): Unit = tree match {
          case PackageDef(pid, _) =>
            val prevPrefix = packagePrefix 
            packagePrefix = prevPrefix+pid+"."
            super.traverse(tree)
            packagePrefix = prevPrefix
          case ClassDef(mods, name, tparams, impl) =>
            addMapping(packagePrefix+name)
          case ModuleDef(_, name, _) if name == nme.PACKAGEkw =>
            super.traverse(tree)
          case ModuleDef(mods, name, impl) =>
            addMapping(packagePrefix+name)
          case _ => super.traverse(tree)
        }
      }

      extractor.traverse(tree) 
    }
  }
  
  def get(qualifiedName : String) = typeToFile.get(qualifiedName)
  
  def remove(file : IFile) {
    fileToTypes.get(file) match {
      case Some(types) => for (tpe <- types) typeToFile.remove(tpe)
      case _ =>
    }
    fileToTypes.remove(file)
  }
  
  def update(file : IFile) {
    if (file.getFileExtension == "scala") {
      remove(file)
      parse(List(file))
    }
  }
  
  /**
   * optimized version of new + files.foreach { f => update }
   * Avoid call of remove, creation of some temporary List
   * @param files
   */
  def resetWith(files : Set[IFile]) {
      fileToTypes.clear
      typeToFile.clear
      parse(files.toList)
  }
}
