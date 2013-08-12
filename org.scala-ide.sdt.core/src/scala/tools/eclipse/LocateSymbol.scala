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

  def findCompilationUnit(sym: Symbol) : Option[InteractiveCompilationUnit]= {

    def enclosingClassForScalaDoc(sym:Symbol): Symbol = {
      if ((sym.isClass || sym.isModule) && sym.isPackage) sym else sym.enclosingPackageClass
    }

    def findClassFile: Option[InteractiveCompilationUnit] = {
      logger.debug("Looking for a classfile for " + sym.fullName)
      val javaProject = project.javaProject.asInstanceOf[JavaProject]
      val packName = askOption { () => enclosingClassForScalaDoc(sym).fullName }
      packName.flatMap{ pn =>
        val name = askOption { () =>
          val top = sym.enclosingTopLevelClass
          if (sym.owner.isPackageObjectClass) "package$.class" else top.name + (if (top.isModuleClass) "$" else "") + ".class"
        }

        name.flatMap { nm =>
          val pfs = new SearchableEnvironment(javaProject, null: WorkingCopyOwner).nameLookup.findPackageFragments(pn, false)

          if (pfs eq null) None else pfs.toStream flatMap { pf =>
            logger.debug("Trying out to get " + nm)
            val cf = pf.getClassFile(nm)
            cf match {
              case classFile : ScalaClassFile =>
                logger.debug("Found Scala class file: " + classFile.getElementName)
                Some(classFile)
              case _ => None
            }
          } headOption
        }
      }
    }

    def findPath: Option[IPath] = {
      logger.info("Looking for a compilation unit for " + sym.fullName)
      val javaProject = project.javaProject.asInstanceOf[JavaProject]
      val nameLookup = new SearchableEnvironment(javaProject, null: WorkingCopyOwner).nameLookup

      val name = askOption { () =>
        if (sym.owner.isPackageObject) sym.owner.owner.fullName + ".package" else sym.enclosingTopLevelClass.fullName
      }
      logger.debug("Looking for compilation unit " + name)
      name.flatMap{ n =>
        Option(nameLookup.findCompilationUnit(n)) map (_.getResource().getFullPath())
      }
    }

    def findSourceFile(): Option[IPath] =
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
      SourceFileProviderRegistry.getProvider(f).createFrom(f)
    }
  }

  @deprecated("Use locate(sym:Symbol): Option[(InteractiveCompilationUnit, Int)]", "4.0.0")
  def locate(sym:Symbol, scu:InteractiveCompilationUnit):Option[(InteractiveCompilationUnit, Int)] = {
    locate(sym) match {
      case Some((foundScu,_)) if (foundScu != scu) => throw new IllegalArgumentException("locate doesn't support searching for a symbol in a bespoke unit anymore")
      case optScCouple => optScCouple
    }
  }

  def locate(sym : Symbol): Option[(InteractiveCompilationUnit, Int)] =
    findCompilationUnit(sym) flatMap { file =>
      val pos = if (sym.pos eq NoPosition) {
        file.withSourceFile { (f, _) =>
          val pos = new Response[Position]
          askLinkPos(sym, f, pos)
          pos.get.left.toOption
        }.flatten
      } else Some(sym.pos)

      pos flatMap { p =>
        if (p eq NoPosition) None
        else Some(file, p.point)
      }
    }
}
