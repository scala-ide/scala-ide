package scala.tools.eclipse.util.parsing

import scalariform.lexer.ScalaLexer
import scalariform.parser.ScalaParser
import scalariform.parser.CompilationUnit
import scalariform.lexer.Token

object ScalariformParser {
  def safeParse(source: String): Option[(CompilationUnit, List[Token])] = {
    val tokens = ScalaLexer.tokenise(source, forgiveErrors = true)
    val parser = new ScalaParser(tokens.toArray)
    parser.safeParse(parser.compilationUnitOrScript) map { (_, tokens) }
  }
}