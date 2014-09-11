package org.scalaide.core.internal.compiler

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
import org.scalaide.core.internal.jdt.model.ScalaSourceFile
import org.scalaide.core.internal.jdt.model.ScalaClassFile
import org.eclipse.core.runtime.IPath
import org.scalaide.core.extensions.SourceFileProviderRegistry
import org.eclipse.jdt.core.IJavaProject
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._

import org.scalaide.core.compiler._

trait LocateSymbol { self: ScalaPresentationCompiler =>

  def findCompilationUnit(sym: Symbol): Option[InteractiveCompilationUnit] = {

   def enclosingClassForScalaDoc(sym:Symbol): Symbol = {
      if ((sym.isClass || sym.isModule) && sym.hasPackageFlag) sym else sym.enclosingPackageClass
    }

    def symClassName(sym: Symbol): Option[String] = askOption { () =>
      val top = sym.enclosingTopLevelClass
      if ((sym != NoSymbol) && sym.owner.isPackageObjectClass) "package$.class" else top.name + (if (top.isModuleClass) "$" else "") + ".class"
      }

    def findClassFile(): Option[InteractiveCompilationUnit] = {
      logger.debug("Looking for a classfile for " + sym.fullName)
      val javaProject = project.javaProject.asInstanceOf[JavaProject]
      val packName = asyncExec { enclosingClassForScalaDoc(sym).fullName }.getOption()

      val name = symClassName(sym)
      packName.flatMap { pn =>
        val pfs = new SearchableEnvironment(javaProject, null: WorkingCopyOwner).nameLookup.findPackageFragments(pn, false)
        name.flatMap { nm =>
          if (pfs eq null) None else pfs.toStream map
            { pf => logger.debug("Trying out to get " + nm); pf.getClassFile(nm) } collectFirst
            {
              case classFile: ScalaClassFile =>
                logger.debug("Found Scala class file: " + classFile.getElementName)
                classFile
            }
        }
      }
    }

    def findPath(): Option[IPath] = {
      logger.info("Looking for a compilation unit for " + sym.fullName)
      val javaProject = project.javaProject.asInstanceOf[JavaProject]
      val nameLookup = new SearchableEnvironment(javaProject, null: WorkingCopyOwner).nameLookup

      val name = asyncExec{
        if ((sym != NoSymbol) && sym.owner.isPackageObject) sym.owner.owner.fullName + ".package" else sym.enclosingTopLevelClass.fullName
      }.getOption()
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
          case _ => findPath()
        }
      } else
        findPath()

    findSourceFile.fold(findClassFile()){ f =>
      SourceFileProviderRegistry.getProvider(f).createFrom(f)
    }
  }

  def findDeclaration(sym: Symbol): Option[(InteractiveCompilationUnit, Int)] =
    findCompilationUnit(sym) flatMap { cunit =>
      val pos = if (sym.pos eq NoPosition) {
        cunit.withSourceFile { (f, _) =>
          val pos = askLinkPos(sym, f)
          pos.get.left.toOption
        }.flatten
      } else Some(sym.pos)

      pos flatMap { p =>
        if (p eq NoPosition) None
        else Some(cunit, p.point)
      }
    }
}
