package scala.tools.eclipse.completion
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import scala.tools.eclipse.ScalaPresentationCompiler
import scala.tools.nsc.util.SourceFile

import scala.collection.mutable

/** Base class for Scala completions. No UI dependency, can be safely used in a
 *  headless testing environement.
 *  
 *  @see scala.tools.eclipse.ui.ScalaCompletinProposalComputer
 */
class ScalaCompletions {
  import org.eclipse.jface.text.IRegion
  
  def findCompletions(region: IRegion)(position: Int, scu: ScalaCompilationUnit)
                             (sourceFile: SourceFile, compiler: ScalaPresentationCompiler): List[CompletionProposal] = {
    
    val pos = compiler.rangePos(sourceFile, position, position, position)
    
    val start = if (region == null) position else region.getOffset
    
    val typed = new compiler.Response[compiler.Tree]
    compiler.askTypeAt(pos, typed)
    val t1 = typed.get.left.toOption

    val completed = new compiler.Response[List[compiler.Member]]
    // completion depends on the typed tree
    t1 match {
      // completion on select
      case Some(s@compiler.Select(qualifier, name)) if qualifier.pos.isDefined && qualifier.pos.isRange =>
        val cpos0 = qualifier.pos.end 
        val cpos = compiler.rangePos(sourceFile, cpos0, cpos0, cpos0)
        compiler.askTypeCompletion(cpos, completed)
      case Some(compiler.Import(expr, _)) =>
        // completion on `imports`
        val cpos0 = expr.pos.endOrPoint
        val cpos = compiler.rangePos(sourceFile, cpos0, cpos0, cpos0)
        compiler.askTypeCompletion(cpos, completed)
      case _ =>
        // this covers completion on `types`
        val cpos = compiler.rangePos(sourceFile, start, start, start)
        compiler.askScopeCompletion(cpos, completed)
    }
    
    val prefix = (if (position <= start) "" else scu.getBuffer.getText(start, position-start).trim).toArray
    
    def nameMatches(sym : compiler.Symbol) = prefixMatches(sym.decodedName.toString.toArray, prefix)  

    val buff = new mutable.ListBuffer[CompletionProposal]
    for (completions <- completed.get.left.toOption) {
      compiler.askOption { () =>
        for (completion <- completions) {
          completion match {
            case compiler.TypeMember(sym, tpe, accessible, inherited, viaView) if !sym.isConstructor && nameMatches(sym) =>
              buff += compiler.mkCompletionProposal(start, sym, tpe, inherited, viaView)
            case compiler.ScopeMember(sym, tpe, accessible, _) if !sym.isConstructor && nameMatches(sym) =>
              buff += compiler.mkCompletionProposal(start, sym, tpe, false, compiler.NoSymbol)
            case _ =>
          }
        }
      }
    }
    
    buff.toList
  }
}