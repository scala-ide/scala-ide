package scala.tools.eclipse.hyperlink.text

import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.hyperlink.IHyperlink
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.InteractiveCompilationUnit

/** A factory that builds IHyperlink instances from compiler Symbols.
 *
 *  It needs to have an instance of the compiler, therefore it is abstract. Use it by
 *  extending it and giving a concrete value to `val global`. Compiler types are
 *  path-dependent, like `global.Symbol`. It is very often the case that you will
 *  need to refine the type of `global` in your concrete instance, so that
 *  `Symbols` coming from your instance of `Global` are considered compatible with
 *  `Symbols` used by this class.
 *
 *  For example:
 *
 *  {{{
 *      scu.withSourceFile({ (sourceFile, compiler) =>
 *        // hard-wire the compiler instance in our hyperlink factory
 *        object DeclarationHyperlinkFactory extends HyperlinkFactory {
 *          protected val global: compiler.type = compiler 
 *        }
 *        // now compiler.Symbol and DeclarationHyperlinkFactory.Symbol are the same type
 *        // because `global` has the singleton type `compiler.type`.
 *      }
 *
 *  }}}
 */
abstract class HyperlinkFactory {
  protected val global: ScalaPresentationCompiler

  def create(createHyperlink: Hyperlink.Factory, sym: global.Symbol, region: IRegion): Option[IHyperlink] = {
    global.locate(sym) map {
      case (f, pos) =>
        val text = sym.kindString + " " + sym.fullName
        createHyperlink(f, pos, sym.name.length, text, region)
    }
  }
}
