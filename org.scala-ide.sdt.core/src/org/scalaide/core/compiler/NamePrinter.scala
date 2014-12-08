package org.scalaide.core.compiler

import scala.concurrent.duration.Duration
import scala.concurrent.duration.MILLISECONDS
import scala.tools.nsc.interactive.Global
import org.eclipse.jface.text.Region
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits.RichResponse
import org.scalaide.util.eclipse.RegionUtils.RichRegion
import scala.reflect.internal.util.SourceFile

private object NamePrinter {
  private case class Location(src: SourceFile, offset: Int)

  private abstract class DeclarationNamePrinter extends DeclarationPrinter {
    import compiler._
  }
}

/**
 * For printing names in an InteractiveCompilationUnit.
 */
class NamePrinter(cu: InteractiveCompilationUnit) {
  import NamePrinter._

  /**
   * Returns the fully qualified name of the symbol at the given offset if available.
   *
   * This method is used by "Copy Qualified Name" in the GUI.
   */
  def qualifiedNameAt(offset: Int): Option[String] = {
    cu.withSourceFile { (src, compiler) =>

      val scalaRegion = new Region(cu.sourceMap(cu.getContents()).scalaPos(offset), 1)
      compiler.askTypeAt(scalaRegion.toRangePos(src)).getOption() match {
        case Some(tree) => qualifiedName(Location(src, offset), compiler)(tree)
        case _ => None
      }
    }.flatten
  }

  private def qualifiedName(loc: Location, comp: IScalaPresentationCompiler)(t: comp.Tree): Option[String] = {
    val resp = comp.asyncExec(qualifiedNameImpl(loc, comp)(t))
    resp.getOption().flatten
  }

  private def qualifiedNameImpl(loc: Location, comp: IScalaPresentationCompiler)(t: comp.Tree): Option[String] = {
    def enclosingMethod(currentTree: comp.Tree, loc: Location) = {
      def isEnclosingMethod(t: comp.Tree) = {
        if (t.isInstanceOf[comp.DefDef]) {
          t.pos.properlyIncludes(currentTree.pos)
        } else {
          false
        }
      }

      comp.askLoadedTyped(loc.src, true).getOption().map { fullTree =>
        comp.locateIn(fullTree, comp.rangePos(loc.src, loc.offset, loc.offset, loc.offset), isEnclosingMethod)
      }
    }

    def qualifiedNameImplPrefix(loc: Location, t: comp.Tree) = {
      enclosingMethod(t, loc) match {
        case Some(comp.EmptyTree) | None => ""
        case Some(encMethod) => qualifiedNameImpl(loc, comp)(encMethod).map(_ + ".").getOrElse("")
      }
    }

    def importDefStr(loc: Location, tree: comp.Tree, selectors: List[comp.ImportSelector]) = {
      def isRelevant(selector: comp.ImportSelector) = {
        selector.name != comp.nme.WILDCARD &&
          selector.name == selector.rename
      }

      qualifiedName(loc: Location, comp)(tree).map { prefix =>
        val suffix = selectors match {
          case List(selector) if isRelevant(selector) => "." + selector.name.toString
          case _ => ""
        }
        prefix + suffix
      }
    }

    def symbolName(symbol: comp.Symbol) = {
      if (symbol.isParameter)
        shortName(symbol.name)
      else
        symbol.fullName
    }

    def vparamssStr(vparamss: List[List[comp.ValDef]]) = {
      if (vparamss.isEmpty) {
        ""
      } else {
        vparamss.map(vparamsStr(_)).mkString("")
      }
    }

    def vparamsStr(vparams: List[comp.ValDef]) = {
      "(" + vparams.map(vparmStr(_)).mkString(", ") + ")"
    }

    def vparmStr(valDef: comp.ValDef) = {
      val name = valDef.name
      val tpt = valDef.tpt

      val declPrinter = new DeclarationNamePrinter {
        val compiler: comp.type = comp
      }

      name.toString + ": " + declPrinter.showType(tpt.tpe)
    }

    def tparamsStr(tparams: List[comp.TypeDef]) = {
      if (tparams.isEmpty) {
        ""
      } else {
        "[" + tparams.map(tparamStr(_)).mkString(", ") + "]"
      }
    }

    def tparamStr(tparam: comp.TypeDef) = {
      shortName(tparam.name)
    }

    def shortName(name: comp.Name) = {
      val fullName = name.toString
      fullName.split(".").lastOption.getOrElse(fullName)
    }

    def identStr(ident: comp.Ident) = {
      ident.name match {
        case _: comp.TypeName => ident.symbol.fullName
        case _ => ident.symbol.nameString
      }
    }

    def valDefStr(valDef: comp.ValDef) = {
      if (valDef.mods.isParamAccessor)
        valDef.symbol.fullName
      else
        valDef.symbol.nameString
    }

    def classDefStr(classDef: comp.ClassDef) = {
      val className = {
        if (classDef.symbol.isLocalToBlock)
          classDef.symbol.nameString
        else
          classDef.symbol.fullName
      }
      className + tparamsStr(classDef.tparams)
    }

    def defDefStr(defDef: comp.DefDef) = {
      val symName = {
        if (defDef.symbol.isLocalToBlock)
          defDef.symbol.nameString
        else
          defDef.symbol.fullName
      }

      symName + tparamsStr(defDef.tparams) + vparamssStr(defDef.vparamss)
    }

    if (t.symbol.isInstanceOf[comp.NoSymbol])
      None
    else {
      val (name, qualify) = t match {
        case comp.Select(qualifier, name) => (Some(t.symbol.fullName), false)
        case defDef: comp.DefDef => (Some(defDefStr(defDef)), true)
        case classDef: comp.ClassDef => (Some(classDefStr(classDef)), true)
        case valDef: comp.ValDef => (Some(valDefStr(valDef)), valDef.mods.isParameter)
        case comp.Import(tree, selectors) => (importDefStr(loc, tree, selectors), false)
        case ident: comp.Ident => (Some(identStr(ident)), true)
        case _ => (Option(t.symbol).map(symbolName(_)), true)
      }

      val prefix = if (qualify) qualifiedNameImplPrefix(loc, t) else ""
      name.map(prefix + _)
    }
  }

}
