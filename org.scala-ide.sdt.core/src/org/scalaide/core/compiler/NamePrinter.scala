package org.scalaide.core.compiler

import scala.concurrent.duration.Duration
import scala.concurrent.duration.MILLISECONDS
import scala.tools.nsc.interactive.Global

import org.eclipse.jface.text.Region
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits.RichResponse
import org.scalaide.util.eclipse.RegionUtils.RichRegion

/**
 * For printing names in an InteractiveCompilationUnit.
 */
class NamePrinter(cu: InteractiveCompilationUnit) {

  /**
   * Returns the fully qualified name of the symbol at the given offset if available.
   *
   * This method is used by "Copy Qualified Name" in the GUI.
   */
  def qualifiedNameAt(offset: Int): Option[String] = {
    cu.withSourceFile { (src, compiler) =>

      val scalaRegion = new Region(cu.sourceMap(cu.getContents()).scalaPos(offset), 1)
      compiler.askTypeAt(scalaRegion.toRangePos(src)).getOption() match {
        case Some(tree) => typeInfo(compiler)(tree)
        case _ => None
      }
    }.flatten
  }

  private def typeInfo(compiler: IScalaPresentationCompiler)(t: compiler.Tree): Option[String] = {
    val resp = compiler.asyncExec(typeInfoImpl(compiler)(t))
    resp.getOption().flatten
  }

  private def typeInfoImpl(compiler: IScalaPresentationCompiler)(t: compiler.Tree): Option[String] = {
    t match {
      case compiler.Select(qualifier, name) =>
        typeInfo(compiler)(qualifier).map(_ + "." + name.toString)
      case defDef: compiler.DefDef =>
        Some(defDef.symbol.fullName + tparamsStr(defDef.tparams) + vparamssStr(compiler)(defDef.vparamss))
      case classDef: compiler.ClassDef =>
        Some(classDef.symbol.fullName + tparamsStr(classDef.tparams))
      case compiler.Import(tree, selectors) => importDefStr(compiler)(tree, selectors)
      case compiler.Ident(name: compiler.TypeName) => Some(shortName(name))
      case _ =>
        Option(t.symbol).map(symbolName(_))
    }
  }

  private def importDefStr(compiler: IScalaPresentationCompiler)(tree: compiler.Tree, selectors: List[compiler.ImportSelector]) = {
    def isRelevant(selector: compiler.ImportSelector) = {
      selector.name != compiler.nme.WILDCARD &&
      selector.name == selector.rename
    }

    typeInfo(compiler)(tree).map { prefix =>
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
      vparamss.map(vparamsStr(_)).mkString("")
    }
  }

  private def vparamsStr(vparams: List[Global#ValDef]) = {
    "(" + vparams.map(vparmStr(_)).mkString(", ") + ")"
  }

  private def vparmStr(valDef: Global#ValDef) = {
    val name = valDef.name
    val tpt = valDef.tpt
    name.toString + ": " + shortName(tpt.symbol.name)
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
