/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.ICodeAssist
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.jdt.core.WorkingCopyOwner
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.ITextViewer
import org.eclipse.jface.text.hyperlink.AbstractHyperlinkDetector
import org.eclipse.jface.text.hyperlink.IHyperlink
import org.eclipse.jdt.internal.core.ClassFile
import org.eclipse.jdt.internal.core.Openable
import org.eclipse.jdt.internal.core.SearchableEnvironment
import org.eclipse.jdt.internal.core.JavaProject
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit
import org.eclipse.jdt.ui.actions.SelectionDispatchAction
import org.eclipse.jdt.internal.ui.javaeditor.EditorUtility
import org.eclipse.jdt.internal.ui.javaeditor.JavaElementHyperlink
import scala.tools.nsc.io.AbstractFile
import javaelements.ScalaSourceFile
import javaelements.ScalaClassFile
import org.eclipse.core.runtime.IPath
import scala.tools.eclipse.sourcefileprovider.SourceFileProviderRegistry

trait LocateSymbol { self : ScalaPresentationCompiler =>

  def locate(sym : Symbol, scu : InteractiveCompilationUnit): Option[(InteractiveCompilationUnit, Int)] = {
    def find[T, V](arr : Array[T])(f : T => Option[V]) : Option[V] = {
      for(e <- arr) {
        f(e) match {
          case v@Some(_) => return v
          case None =>
        }
      }
      None
    }
    def findClassFile(): Option[ScalaClassFile] = {
      logger.debug("Looking for a classfile for " + sym.fullName)
      val packName = sym.enclosingPackage.fullName
      val project = scu.scalaProject.javaProject.asInstanceOf[JavaProject]
      val pfs = new SearchableEnvironment(project, null: WorkingCopyOwner).nameLookup.findPackageFragments(packName, false)
      if (pfs eq null) None else find(pfs) { pf =>
        val top = sym.enclosingTopLevelClass
        val name = if (sym.owner.isPackageObjectClass) "package$.class" else top.name + (if (top.isModuleClass) "$" else "") + ".class"
        logger.debug("Trying out to get " + name)
        val cf = pf.getClassFile(name)
        cf match {
          case classFile : ScalaClassFile =>
            logger.debug("Found Scala class file: " + classFile.getElementName)
            Some(classFile)
          case _ => None
        }
      }
    }

    def findCompilationUnit(): Option[IPath] = {
      logger.info("Looking for a compilation unit for " + sym.fullName)
      val project = scu.scalaProject.javaProject.asInstanceOf[JavaProject]
      val nameLookup = new SearchableEnvironment(project, null: WorkingCopyOwner).nameLookup

      val name = if (sym.owner.isPackageObject) sym.owner.owner.fullName + ".package" else sym.enclosingTopLevelClass.fullName
      logger.debug("Looking for compilation unit " + name)
      Option(nameLookup.findCompilationUnit(name)) map (_.getResource().getFullPath())
    }

    def findSourceFile(): Option[IPath] =
      if (sym.sourceFile ne null) {
        val path = new Path(sym.sourceFile.path)
        val root = ResourcesPlugin.getWorkspace().getRoot()
        root.findFilesForLocation(path) match {
          case arr: Array[_] if arr.length == 1 => Some(arr(0).getFullPath)
          case _                                => findCompilationUnit()
        }
      } else
        findCompilationUnit()

    val sourceFile = findSourceFile()

    val target =
      if(sourceFile.isDefined)
        SourceFileProviderRegistry.getProvider(sourceFile.get).createFrom(sourceFile.get)
      else
        findClassFile()

    target flatMap { file =>
      val pos = if (sym.pos eq NoPosition) {
        file.withSourceFile { (f, _) =>
          val pos = new Response[Position]
          askLinkPos(sym, f, pos)
          pos.get.left.toOption
        }.flatten
      } else Some(sym.pos)

      pos flatMap { p =>
        if (p eq NoPosition) None
        else Some(file -> p.point)
      }
    }
  }
}
