package scala.tools.eclipse.hyperlink.text.detector


import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.hyperlink.IHyperlink
import scala.Option.option2Iterable
import scala.tools.eclipse.{ScalaPresentationCompiler => compiler}
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.logging.HasLogger
import scala.tools.eclipse.hyperlink.text._

private[hyperlink] class ScalaDeclarationHyperlinkComputer extends HasLogger {
  def findHyperlinks(scu: ScalaCompilationUnit, wordRegion: IRegion): Option[List[IHyperlink]] = {
    scu.withSourceFile({ (sourceFile, compiler) =>
      object DeclarationHyperlinkFactory extends HyperlinkFactory {
        protected val global: compiler.type = compiler
      }
      
      if (wordRegion == null || wordRegion.getLength == 0)
        None
      else {
        val start = wordRegion.getOffset
        val regionEnd = wordRegion.getOffset + wordRegion.getLength
        // removing 1 handles correctly hyperlinking requests @ EOF
        val end = if (sourceFile.length == regionEnd) regionEnd - 1 else regionEnd

        val pos = compiler.rangePos(sourceFile, start, start, end)

        import compiler.{ log => _, _ }

        val response = new Response[compiler.Tree]
        askTypeAt(pos, response)
        val typed = response.get

        logger.info("detectHyperlinks: wordRegion = " + wordRegion)
        compiler.askOption { () =>
          typed.left.toOption map {
            case Import(expr, sels) =>
              if (expr.pos.includes(pos)) {
                @annotation.tailrec
                def locate(p: Position, inExpr: Tree): Symbol = inExpr match {
                  case Select(qualifier, name) =>
                    if (qualifier.pos.includes(p)) locate(p, qualifier)
                    else inExpr.symbol
                  case tree => tree.symbol
                }

                List(locate(pos, expr))
              } else {
                sels find (selPos => selPos.namePos >= pos.start && selPos.namePos <= pos.end) map { sel =>
                  val tpe = stabilizedType(expr)
                  List(tpe.member(sel.name), tpe.member(sel.name.toTypeName))
                } getOrElse Nil
              }
            case Annotated(atp, _) => List(atp.symbol)
            case st: SymTree => List(st.symbol)
            case t => logger.info("unhandled tree " + t.getClass); List()
          } flatMap { list =>
            val filteredSyms = list filterNot { sym => sym.isPackage || sym == NoSymbol }
            if (filteredSyms.isEmpty) None else Some(
              filteredSyms.foldLeft(List[IHyperlink]()) { (links, sym) =>
                if (sym.isJavaDefined) links
                else
                  DeclarationHyperlinkFactory.create(Hyperlink.withText("Open Declaration"), scu, sym, wordRegion).toList ::: links
              })
          }
        }.flatten.headOption match {
          case links @ Some(List()) =>
            logger.info("Falling back to selection engine for %s!".format(typed.left))
            links
          case links =>
            links
        }
      }
    })(None)
  }

}