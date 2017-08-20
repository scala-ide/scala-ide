package org.scalaide.core.compiler

import scala.reflect.internal.util.SourceFile

import org.eclipse.jface.text.Region
import org.scalaide.logging.HasLogger
import org.scalaide.util.eclipse.RegionUtils.RichRegion

private object NamePrinter {
  private case class Location(src: SourceFile, offset: Int)
  private val RxAnonOrRefinementString = """<(?:\$anon:|refinement of) (.+)>""".r
}

/**
 * For printing names in an InteractiveCompilationUnit.
 */
class NamePrinter(cu: InteractiveCompilationUnit) extends HasLogger {
  import NamePrinter._

  /**
   * Returns the fully qualified name of the symbol at the given offset if available.
   *
   * This method is used by 'Copy Qualified Name' in the GUI. Please note that there is no formal
   * specification of the names this feature should return. The behavior of the implementation is mostly modeled
   * after the corresponding JDT feature.
   *
   * @note Important: This method assumes the `cu` is already loaded in the presentation compiler (for example,
   *                  `cu.initialReconcile` was called on it). If the unit is not loaded, this might fail spuriously.
   */
  def qualifiedNameAt(offset: Int): Option[String] = {
    cu.withSourceFile { (src, compiler) =>
      val scalaRegion = new Region(cu.sourceMap(cu.getContents()).scalaPos(offset), 1)

      for {
        tree <-  handleCompilerResponse(compiler.askTypeAt(scalaRegion.toRangePos(src)).get)
        fullTree <- handleCompilerResponse(compiler.askLoadedTyped(src, true).get)
        qname <- handleCompilerResponse(compiler.asyncExec(qualifiedName(Location(src, offset), compiler)(fullTree, tree)).get)
      } yield qname

    }.flatten.flatten
  }

  private def handleCompilerResponse[T](res: Either[T, Throwable]): Option[T] = res match {
    case Left(t) => Option(t)
    case Right(th) =>
      logger.error("Error computing qualfied name", th)
      None
  }

  private def qualifiedName(loc: Location, comp: IScalaPresentationCompiler)(fullTree: comp.Tree, t: comp.Tree): Option[String] = {
    def enclosingDefinition(currentTree: comp.Tree, loc: Location) = {
      def isEnclosingDefinition(t: comp.Tree) = t match {
        case _: comp.DefDef | _: comp.ClassDef | _: comp.ModuleDef | _: comp.PackageDef =>
          t.pos.properlyIncludes(currentTree.pos)
        case _ => false
      }

      comp.locateIn(fullTree, comp.rangePos(loc.src, loc.offset, loc.offset, loc.offset), isEnclosingDefinition)
    }

    def qualifiedNameImplPrefix(loc: Location, t: comp.Tree) = {
      enclosingDefinition(t, loc) match {
        case comp.EmptyTree => ""
        case encDef => qualifiedName(loc, comp)(fullTree, encDef).map(_ + ".").getOrElse("")
      }
    }

    def handleImport(loc: Location, tree: comp.Tree, selectors: List[comp.ImportSelector]) = {
      def isRelevant(selector: comp.ImportSelector) = {
        selector.name != comp.nme.WILDCARD &&
          selector.name == selector.rename
      }

      val suffix = selectors match {
        case List(selector) if isRelevant(selector) => "." + selector.name.toString
        case _ => ""
      }

      (Option(tree.symbol).map(_.fullName + suffix), false)
    }

    def symbolName(symbol: comp.Symbol) = {
      if (symbol.isParameter)
        shortName(symbol.name)
      else
        symbol.fullName
    }

    def paramssStr(paramss: List[List[comp.Symbol]]) = {
     if (paramss.isEmpty) ""
     else paramss.map(paramsStr(_)).mkString("")
    }

    def paramsStr(params: List[comp.Symbol]) = {
      "(" + params.map(paramStr(_)).mkString(", ") + ")"
    }

    def paramStr(param: comp.Symbol) = {
      param.name + ": " + declPrinterTypeStr(param.tpe)
    }

    def vparamssStr(vparamss: List[List[comp.ValDef]]) = {
      paramssStr(vparamss.map(_.map(_.symbol)))
    }

    def tparamsStrFromTypeDefs(tparams: List[comp.TypeDef]) = {
      tparamsStrFromSyms(tparams.map(_.symbol))
    }

    def tparamsStrFromSyms(tparams: List[comp.Symbol]) = {
      if (tparams.isEmpty) ""
      else "[" + tparams.map(tparamStr(_)).mkString(", ") + "]"
    }

    def tparamStr(sym: comp.Symbol) = {
      shortName(sym.name)
    }

    def declPrinterTypeStr(tpe: comp.Type) = {
      new DeclarationPrinter {
        val compiler: comp.type = comp
      }.showType(tpe)
    }

    def shortName(name: comp.Name) = {
      val fullName = name.toString
      fullName.split(".").lastOption.getOrElse(fullName)
    }

    def handleIdent(ident: comp.Ident) = {
      ident.name match {
        case _: comp.TypeName => (Some(ident.symbol.fullName), false)
        case _ => (Some(ident.symbol.nameString), true)
      }
    }

    def handleValDef(valDef: comp.ValDef) = {
      (Some(valDef.symbol.nameString), true)
    }

    def handleClassDef(classDef: comp.ClassDef) = {
      val (className, qualifiy) = classDef.symbol match {
        case classSym: comp.ClassSymbol if (classSym.isAnonymousClass) =>
          (anonClassSymStr(classSym), true)
        case sym =>
          (sym.nameString, true)
      }

      (Some(className + tparamsStrFromTypeDefs(classDef.tparams)), qualifiy)
    }

    def handledefDef(defDef: comp.DefDef) = {
      val symName = defDef.symbol.nameString
      (Some(symName + tparamsStrFromTypeDefs(defDef.tparams) + vparamssStr(defDef.vparamss)), true)
    }

    def anonClassSymStr(classSym: comp.ClassSymbol) = {
      // Using a regular expression to extract information from anonOrRefinementString is not
      // ideal, but the only easy way I found to reuse the functionality already implemented
      // by Definitions.parentsString.
      val symStr = classSym.anonOrRefinementString match {
        case RxAnonOrRefinementString(symStr) => symStr
        case _ => classSym.toString
      }
      s"new $symStr {...}"
    }

    def handleSelect(select: comp.Select) = {
      (Some(selectStr(select)), false)
    }

    def selectStr(select: comp.Select) = select match {
      case comp.Select(comp.Block(List(stat: comp.ClassDef), _), _) if stat.symbol.isAnonOrRefinementClass && stat.symbol.isInstanceOf[comp.ClassSymbol] =>
        anonClassSymStr(stat.symbol.asInstanceOf[comp.ClassSymbol]) + "." + select.symbol.nameString
      case comp.Select(qualifier, _) =>
        if (select.symbol.isConstructor) qualifier.tpe.toString
        else select.symbol.fullName
    }

    def handleModuleDef(moduleDef: comp.ModuleDef) = {
      if (moduleDef.symbol.isPackageObject)
        (Some(moduleDef.symbol.owner.fullName), false)
      else
        (Some(moduleDef.symbol.name.toString), true)
    }

    def handlePackageDef(packageDef: comp.PackageDef) = {
      val name = {
        if (packageDef.symbol.isEmptyPackage) None
        else Some(packageDef.symbol.fullName)
      }

      (name, false)
    }

    def handleApply(apply: comp.Apply) = {
      val symInfo = apply.symbol.info
      val paramsStr = tparamsStrFromSyms(symInfo.typeParams) + paramssStr(symInfo.paramss)

      val name = apply.fun match {
        case select: comp.Select => selectStr(select)
        case _ => apply.symbol.fullName
      }

      (Some(name + paramsStr), false)
    }

    if (t.symbol.isInstanceOf[comp.NoSymbol])
      None
    else {
      val (name, qualify) = t match {
        case select: comp.Select => handleSelect(select)
        case defDef: comp.DefDef => handledefDef(defDef)
        case classDef: comp.ClassDef => handleClassDef(classDef)
        case moduleDef: comp.ModuleDef => handleModuleDef(moduleDef)
        case valDef: comp.ValDef => handleValDef(valDef)
        case comp.Import(tree, selectors) => handleImport(loc, tree, selectors)
        case ident: comp.Ident => handleIdent(ident)
        case packageDef: comp.PackageDef => handlePackageDef(packageDef)
        case apply: comp.Apply => handleApply(apply)
        case _ => (Option(t.symbol).map(symbolName(_)), true)
      }

      val prefix = if (qualify) qualifiedNameImplPrefix(loc, t) else ""
      name.map(prefix + _)
    }
  }
}
