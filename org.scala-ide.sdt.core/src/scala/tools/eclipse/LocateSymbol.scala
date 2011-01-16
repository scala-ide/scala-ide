/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import scala.tools.eclipse.internal.logging.Defensive
import org.eclipse.core.resources.ResourcesPlugin
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.{ICodeAssist, IJavaElement}
import org.eclipse.jface.text.{IRegion, ITextViewer}
import org.eclipse.jface.text.hyperlink.{AbstractHyperlinkDetector, IHyperlink}
import org.eclipse.jdt.internal.core.{ClassFile,Openable}
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit
import org.eclipse.jdt.ui.actions.SelectionDispatchAction
import org.eclipse.jdt.internal.ui.javaeditor.{EditorUtility, JavaElementHyperlink}

import scala.reflect.generic.Flags._
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.util.NoPosition

import javaelements.{ScalaSourceFile, ScalaClassFile, ScalaCompilationUnit}

trait LocateSymbol { self : ScalaPresentationCompiler => 
  
  def locate(sym : Symbol, scu : ScalaCompilationUnit) = {
    def find[T, V](arr : Array[T])(f : T => Option[V]) : Option[V] = {
      for(e <- arr) {
        f(e) match {
          case v@Some(_) => return v
          case None =>
        }
      }
      None
    }
    def findClassFile = {
      val packName = sym.enclosingPackage.fullName
      val pfs = scu.newSearchableEnvironment.nameLookup.findPackageFragments(packName, false)
      if (pfs eq null) None else find(pfs) {
        val top = sym.toplevelClass
        val name = top.name + (if (top.isModule) "$" else "") + ".class"
        _.getClassFile(name) match {
          case classFile : ScalaClassFile => Some(classFile)
          case _ => None
        }
      }
    }
    (if (sym.sourceFile ne null) {
       val path = new Path(sym.sourceFile.path)
       val root = ResourcesPlugin.getWorkspace().getRoot()
       root.findFilesForLocation(path) match {
         case arr : Array[_] if arr.length == 1 =>
           ScalaSourceFile.createFromPath(arr(0).getFullPath.toString)
         case _ => findClassFile
       }
    } else findClassFile) flatMap { file =>
      if (sym.pos eq NoPosition) {
        object traverser {
          var owners = sym.ownerChain.reverse
          def equiv(src : Symbol, clz : Symbol) = {
            src.decodedName == clz.decodedName && ( 
              if (src.isMethod && clz.isMethod) 
                src.info.toString == clz.info.toString
              else src.hasFlag(PACKAGE) && clz.hasFlag(PACKAGE) ||
                   src.isType && clz.isType && !clz.isModuleClass ||
                   src.isTerm && (clz.isTerm || clz.isModuleClass) 
            )
          }
                  
          def traverse(srcsym : Symbol) : Boolean = {
            if (equiv(srcsym, owners.head)) owners.tail match {
              case Nil  => if (srcsym.pos ne NoPosition) { sym.setPos(srcsym.pos); true } else false
              case tl => {
                owners = tl
                srcsym.info.decls exists { traverse _ }
              }
            } else false
          }
        }
        file.withSourceFile{ (f, _) =>
          Defensive.tryOrLog(None : Option[RichCompilationUnit]) {
            def f0(symbol : Symbol) : Option[RichCompilationUnit] = {
              traverser traverse (
                symbol.
                ownerChain.
                reverse.
                head
              )
              reload(List(f), new Response[Unit])
              removeUnitOf(f)
            }
            for (
              root <- Option(root(f)); // root(f) can be null
              symbol <- Option(root.symbol) ;
              r <- f0(symbol)
            ) yield r
          }
        }
      }
      sym.pos match {
        case NoPosition => None
        case pos => Some(file, pos.point) 
      }
    }
  }
}