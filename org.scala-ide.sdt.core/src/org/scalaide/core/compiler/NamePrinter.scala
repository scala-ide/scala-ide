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

  private def qualifiedName(loc: Location, compiler: IScalaPresentationCompiler)(t: compiler.Tree): Option[String] = {
    val resp = compiler.asyncExec(qualifiedNameImpl(loc, compiler)(t))
    resp.getOption().flatten
  }

  private def qualifiedNameImplOld(loc: Location, c: IScalaPresentationCompiler)(t: c.Tree): Option[String] = {
    val declPrinter = new DeclarationNamePrinter {
      val compiler: c.type = c
    }

    Option(t.symbol).map(declPrinter.defString(_, showKind = false)())
  }

  private def qualifiedNameImpl(loc: Location, compiler: IScalaPresentationCompiler)(t: compiler.Tree): Option[String] = {
    if (t.symbol.isInstanceOf[compiler.NoSymbol])
      None
    else {
      val (name, qualify) = t match {
        case compiler.Select(qualifier, name) =>
          (qualifiedNameImpl(loc, compiler)(qualifier).map(_ + "." + name.toString), false)
        case defDef: compiler.DefDef =>
          (Some(defDef.symbol.fullName + tparamsStr(defDef.tparams) + vparamssStr(compiler)(defDef.vparamss)), true)
        case classDef: compiler.ClassDef =>
          (Some(classDef.symbol.fullName + tparamsStr(classDef.tparams)), false)
        case valDef: compiler.ValDef => (Some(valDef.symbol.fullName), true)
        case compiler.Import(tree, selectors) => (importDefStr(loc, compiler)(tree, selectors), false)
        case compiler.Ident(name: compiler.TypeName) => (Some(shortName(name)), true)
        case _ =>
          (Option(t.symbol).map(symbolName(_)), true)
      }

      val prefix = if (qualify) qualifiedNameImplPrefix(loc, compiler)(t) else ""
      name.map(prefix + _)
    }
  }

  private def qualifiedNameImplPrefix(loc: Location, compiler: IScalaPresentationCompiler)(t: compiler.Tree) = {
    Option(compiler.enclosingMethd(loc.src, loc.offset)) match {
      case Some(encMethod) =>
        if (t.pos == encMethod.pos) {
          ""
        } else {
          qualifiedNameImpl(loc, compiler)(encMethod).map(_ + ".").getOrElse("")
        }
      case _ => ""
    }
  }

  private def importDefStr(loc: Location, compiler: IScalaPresentationCompiler)(tree: compiler.Tree, selectors: List[compiler.ImportSelector]) = {
    def isRelevant(selector: compiler.ImportSelector) = {
      selector.name != compiler.nme.WILDCARD &&
      selector.name == selector.rename
    }

    qualifiedName(loc: Location, compiler)(tree).map { prefix =>
      val suffix = selectors match {
        case List(selector) if isRelevant(selector) => "." + selector.name.toString
        case _ => ""
      }
      prefix + suffix
    }
  }

  private def symbolName(symbol: Global#Symbol) = {
    if (symbol.isParameter)
      shortName(symbol.name)
    else
      symbol.fullName
  }

  private def vparamssStr(compiler: IScalaPresentationCompiler)(vparamss: List[List[compiler.ValDef]]) = {
    if (vparamss.isEmpty) {
      ""
    } else {
      vparamss.map(vparamsStr(compiler)(_)).mkString("")
    }
  }

  private def vparamsStr(compiler: IScalaPresentationCompiler)(vparams: List[compiler.ValDef]) = {
    "(" + vparams.map(vparmStr(compiler)(_)).mkString(", ") + ")"
  }

  private def vparmStr(comp: IScalaPresentationCompiler)(valDef: comp.ValDef) = {
    val name = valDef.name
    val tpt = valDef.tpt

    val declPrinter = new DeclarationNamePrinter {
      val compiler: comp.type = comp
    }

    name.toString + ": " + declPrinter.showType(tpt.tpe)
  }

  private def tparamsStr(tparams: List[Global#TypeDef]) = {
    if (tparams.isEmpty) {
      ""
    } else {
      "[" + tparams.map(tparamStr(_)).mkString(", ") + "]"
    }
  }

  private def tparamStr(tparam: Global#TypeDef) = {
    shortName(tparam.name)
  }

  private def shortName(name: Global#Name) = {
    val fullName = name.toString
    fullName.split(".").lastOption.getOrElse(fullName)
  }
}
