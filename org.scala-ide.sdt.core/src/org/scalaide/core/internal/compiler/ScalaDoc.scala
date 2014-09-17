/*
 */
package org.scalaide.core.internal.compiler

import scala.tools.nsc.doc.html.HtmlPage
import scala.tools.nsc.doc.base._
import scala.tools.nsc.doc.base.comment._
import scala.reflect.internal.util.SourceFile
import org.eclipse.jface.internal.text.html.BrowserInformationControlInput
import scala.xml.NodeSeq
import scala.tools.nsc.interactive.Response
import scala.reflect.api.Position
import org.scalaide.core.compiler.IScalaPresentationCompiler.Implicits._
import org.eclipse.jdt.core.IJavaElement
import scala.reflect.internal.Flags
import org.eclipse.jdt.core.IJavaProject

trait Scaladoc extends MemberLookupBase with CommentFactoryBase { this: ScalaPresentationCompiler =>
  val global: this.type = this

  // @see the corresponding member in
  // src/scaladoc/scala/tools/nsc/doc/ScaladocAnalyzer.scala
  override def chooseLink(links: List[LinkTo]): LinkTo = links.headOption.orNull

  override def internalLink(sym: Symbol, site: Symbol): Option[LinkTo] = {
    assert(onCompilerThread, "!onCompilerThread")
    if (sym.isClass || sym.isModule)
      Some(LinkToTpl(sym))
    else
      if ((site.isClass || site.isModule) && site.info.members.toList.contains(sym))
        Some(LinkToMember(sym, site))
      else None
  }

  override def toString(link: LinkTo): String = {
    assert(onCompilerThread, "!onCompilerThread")
    link match {
      case LinkToMember(mbr: Symbol, site: Symbol) =>
        mbr.signatureString + " in " + site.toString
      case LinkToTpl(sym: Symbol) => sym.toString
      case _ => link.toString
    }
  }

  override def warnNoLink: Boolean = false
  override def findExternalLink(sym: Symbol, name: String): Option[LinkToExternal] = None

  def parsedDocComment(sym: Symbol, site: Symbol, javaProject:IJavaProject): Option[Comment] = {
    val res =

      for (u <- findCompilationUnit(sym, javaProject)) yield u.withSourceFile { (source, _) =>

        def listFragments(syms:List[Symbol]): List[(Symbol, SourceFile)] = syms flatMap ((sym) =>
          findCompilationUnit(sym, javaProject) flatMap {_.withSourceFile { (source, _) => (sym,source)}}
        )

        def withFragments(fragments: List[(Symbol, SourceFile)]): Option[(String, String, Position)] = {
          asyncDocComment(sym, source, site, fragments).getOption()
        }

        asyncExec {
          if (sym.owner.hasPackageFlag) sym.baseClasses else sym::sym.allOverriddenSymbols:::site.baseClasses
        }.getOption() flatMap { syms =>
          withFragments(listFragments(syms)) flatMap {
            case (expanded, raw, pos) if !expanded.isEmpty =>
              asyncExec{ parseAtSymbol(expanded, raw, pos, site) }.getOption()
            case _ =>
              None
          }
        }
      } getOrElse (None)
    res.flatten
  }

  def headerForSymbol(sym:Symbol, tpe: Type): Option[String] = asyncExec{
    def compose(ss: List[String]): String = ss.filterNot(_.isEmpty).mkString(" ")

    def defString(sym: Symbol, tpe: Type): String = {
      // NoType is returned for defining occurrences, in this case we want to display symbol info itself.
      val tpeinfo = if (tpe ne NoType) tpe.widen else sym.info
      compose(List(sym.flagString(Flags.ExplicitFlags), sym.keyString, sym.varianceString + sym.nameString +
        sym.infoString(tpeinfo)))
    }

    if (sym.isClass || sym.isModule) sym.fullNameString else {
      val tp = sym.tpe.asSeenFrom(tpe.widen, sym.enclClass)
      defString(sym, tp)
    }
  }.getOption()

}