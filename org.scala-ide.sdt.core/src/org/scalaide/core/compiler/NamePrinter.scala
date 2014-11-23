package org.scalaide.core.compiler

import org.eclipse.jface.text.Region
import org.scalaide.util.eclipse.RegionUtils
import scala.reflect.api.Trees
import org.scalaide.logging.HasLogger
import scala.tools.nsc.interactive.Global

/**
 * For printing names in an InteractiveCompilationUnit.
 */
class NamePrinter(cu: InteractiveCompilationUnit) {
  def qualifiedNameAt(offset: Int): Option[String] = {
    cu.withSourceFile { (src, compiler) =>
      import RegionUtils.RichRegion
      import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits.RichResponse

      val scalaRegion = new Region(cu.sourceMap(cu.getContents()).scalaPos(offset), 1)
      compiler.askTypeAt(scalaRegion.toRangePos(src)).getOption() match {
        case Some(tree) => typeInfo(compiler)(tree)
        case _ => None
      }
    }.flatten
  }

  private def typeInfo(compiler: IScalaPresentationCompiler)(t: compiler.Tree): Option[String] = {
    t match {
      case compiler.Select(qalifier, name) =>
        typeInfo(compiler)(qalifier).map(_ + "." + name.toString)
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

  private def debugInfo(symbol: Global#Symbol) {
    println(s"symbol.fullName: ${symbol.fullName}")
    println(s"symbol.isAbstractType: ${symbol.isAbstractType}")
    println(s"symbol.isTypeParameter: ${symbol.isTypeParameter}")
    println(s"symbol.isAliasType: ${symbol.isAliasType}")
    println(s"symbol.isHigherOrderTypeParameter: ${symbol.isHigherOrderTypeParameter}")
    println(s"symbol.isFreeType: ${symbol.isFreeType}")
    println(s"symbol.isType: ${symbol.isType}")
    println(s"symbol.isParameter: ${symbol.isParameter}")
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

  private def vparmStr(compiler: IScalaPresentationCompiler)(valDef: compiler.ValDef) = {
    val name = valDef.name
    val tpt = valDef.tpt
    name.toString + ": " + typeInfo(compiler)(tpt).get
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
