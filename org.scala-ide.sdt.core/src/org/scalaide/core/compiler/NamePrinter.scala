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
    def qualifiedNameImplPrefix(loc: Location, t: comp.Tree) = {
      Option(comp.enclosingMethd(loc.src, loc.offset)) match {
        case Some(encMethod) =>
          if (t.pos == encMethod.pos) {
            ""
          } else {
            qualifiedNameImpl(loc, comp)(encMethod).map(_ + ".").getOrElse("")
          }
        case _ => ""
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

    if (t.symbol.isInstanceOf[comp.NoSymbol])
      None
    else {
      val (name, qualify) = t match {
        case comp.Select(qualifier, name) =>
          (qualifiedNameImpl(loc, comp)(qualifier).map(_ + "." + name.toString), false)
        case defDef: comp.DefDef =>
          (Some(defDef.symbol.fullName + tparamsStr(defDef.tparams) + vparamssStr(defDef.vparamss)), true)
        case classDef: comp.ClassDef =>
          (Some(classDef.symbol.fullName + tparamsStr(classDef.tparams)), false)
        case valDef: comp.ValDef => (Some(valDef.symbol.fullName), true)
        case comp.Import(tree, selectors) => (importDefStr(loc, tree, selectors), false)
        case comp.Ident(name: comp.TypeName) => (Some(shortName(name)), true)
        case _ =>
          (Option(t.symbol).map(symbolName(_)), true)
      }

      val prefix = if (qualify) qualifiedNameImplPrefix(loc, t) else ""
      name.map(prefix + _)
    }
  }

}
