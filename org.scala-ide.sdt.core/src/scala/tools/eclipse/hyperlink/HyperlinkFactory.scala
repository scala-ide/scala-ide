package scala.tools.eclipse.hyperlink

import scala.tools.eclipse.ScalaPresentationCompiler
import org.eclipse.jface.text.hyperlink.IHyperlink
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import org.eclipse.jface.text.Region
import scala.tools.eclipse.hyperlink.text.Hyperlink
import org.eclipse.jface.text.IRegion

private[hyperlink] abstract class HyperlinkFactory {
  protected val global: ScalaPresentationCompiler

  protected def create(createHyperlink: Hyperlink.Factory, scu: ScalaCompilationUnit, sym: global.Symbol, region: IRegion): Option[IHyperlink] = {
    global.askOption { () =>
      global.locate(sym, scu) map {
        case (f, pos) =>
          val text = sym.kindString + " " + sym.fullName
          createHyperlink(f, pos, region.getLength, text, region)
      }
    }.getOrElse(None)
  }
}

abstract class ImplicitHyperlinkFactory extends HyperlinkFactory {
  def create(scu: ScalaCompilationUnit, t: global.ApplyImplicitView, region: IRegion): Option[IHyperlink] = {
    super.create(Hyperlink.toImplicit, scu, t.symbol, region)
  }
}

abstract class DeclarationHyperlinkFactory extends HyperlinkFactory {
  def create(scu: ScalaCompilationUnit, sym: global.Symbol, region: IRegion): Option[IHyperlink] = {
    super.create(Hyperlink.toDeclaration, scu, sym, region)
  }
}