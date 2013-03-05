/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.{ICodeAssist, IJavaElement, WorkingCopyOwner}
import org.eclipse.jface.text.{IRegion, ITextViewer}
import org.eclipse.jface.text.hyperlink.{AbstractHyperlinkDetector, IHyperlink}
import org.eclipse.jdt.internal.core.{ClassFile,Openable, SearchableEnvironment, JavaProject}
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit
import org.eclipse.jdt.ui.actions.SelectionDispatchAction
import org.eclipse.jdt.internal.ui.javaeditor.{EditorUtility, JavaElementHyperlink}
import tools.nsc.symtab.Flags._
import scala.tools.nsc.io.AbstractFile
import javaelements.{ScalaSourceFile, ScalaClassFile}
import org.eclipse.core.runtime.IPath
import scala.tools.eclipse.sourcefileprovider.SourceFileProviderRegistry

trait LocateSymbol { self : ScalaPresentationCompiler => 

  def findCompilationUnit(sym: Symbol) = {
    def findClassFile: Option[InteractiveCompilationUnit] = {
      logger.debug("Looking for a classfile for " + sym.fullName)
      val packName = sym.enclosingPackageClass.fullName
      val javaProject = project.javaProject.asInstanceOf[JavaProject]
      val pfs = new SearchableEnvironment(javaProject, null: WorkingCopyOwner).nameLookup.findPackageFragments(packName, false)
      if (pfs eq null) None else pfs.toStream flatMap { pf =>
        val name = ask { () =>
          val top = sym.enclosingTopLevelClass
          if (sym.owner.isPackageObjectClass) "package$.class" else top.name + (if (top.isModuleClass) "$" else "") + ".class"
        }
        logger.debug("Trying out to get " + name)
        val cf = pf.getClassFile(name)
        cf match {
          case classFile : ScalaClassFile => 
            logger.debug("Found Scala class file: " + classFile.getElementName)
            Some(classFile)
          case _ => None
        }
      } headOption
    }
    
    def findPath: Option[IPath] = {
      logger.info("Looking for a compilation unit for " + sym.fullName)
      val javaProject = project.javaProject.asInstanceOf[JavaProject]
      val nameLookup = new SearchableEnvironment(javaProject, null: WorkingCopyOwner).nameLookup
      
      val name = ask { () => 
        if (sym.owner.isPackageObject) sym.owner.owner.fullName + ".package" else sym.enclosingTopLevelClass.fullName
      }
      logger.debug("Looking for compilation unit " + name)
      Option(nameLookup.findCompilationUnit(name)) map (_.getResource().getFullPath())
    }
    
    def findSourceFile: Option[IPath] =
      if (sym.sourceFile ne null) {
        val path = new Path(sym.sourceFile.path)
        val root = ResourcesPlugin.getWorkspace().getRoot()
        root.findFilesForLocationURI(path.toFile.toURI) match {
          case Array(f) => Some(f.getFullPath)
          case _        => findPath
        }
      } else
        findPath
    
    findSourceFile.fold(findClassFile) { f =>
      SourceFileProviderRegistry.getProvider(f) flatMap (_.createFrom(f))
    }
  }

  def locate(sym : Symbol): Option[(InteractiveCompilationUnit, Int)] =
    findCompilationUnit(sym) flatMap { file =>
      val pos = if (sym.pos eq NoPosition) {
        file.withSourceFile { (f, _) =>
          val pos = new Response[Position]
          askLinkPos(sym, f, pos)
          pos.get.left.toOption
        } (None)
      } else 
        Some(sym.pos)
        
      pos flatMap { p =>
        if (p eq NoPosition) None 
        else Some(file, p.point)
      }
    }
}
