package scala.tools.eclipse.semantichighlighting.classifier
import scalariform.lexer.ScalaLexer
import scalariform.parser._
import scalariform.utils.Range
import scalariform.lexer.Token

// Symbol information derived by purely syntactic means, via Scalariform's parser, because it (appears) 
// difficult to get this out scalac trees
case class SyntacticInfo(
  namedArgs: Set[Region],
  forVals: Set[Region],
  maybeSelfRefs: Set[Region],
  maybeClassOfs: Set[Region],
  annotations: Set[Region])

object SyntacticInfo {

  private def safeParse(source: String): Option[(CompilationUnit, List[Token])] = {
    val (hiddenTokenInfo, tokens) = ScalaLexer.tokeniseFull(source, forgiveErrors = true)
    val parser = new ScalaParser(tokens.toArray)
    parser.safeParse(parser.compilationUnitOrScript) map { (_, tokens) }
  }

  private implicit def range2Region(range: Range): Region = Region(range.offset, range.length)

  def noSyntacticInfo = SyntacticInfo(Set(), Set(), Set(), Set(), Set())
  
  def getSyntacticInfo(source: String): SyntacticInfo = {
    var namedArgs: Set[Region] = Set()
    var forVals: Set[Region] = Set()
    var maybeSelfRefs: Set[Region] = Set()
    var maybeClassOfs: Set[Region] = Set()
    var annotations: Set[Region] = Set()

    def scan(astNode: AstNode) {
      astNode match {
        case Argument(Expr(List(EqualsExpr(List(CallExpr(None, id, None, Nil, None)), _, _)))) =>
          namedArgs += id.range
        case Generator(_, Expr(List(GeneralTokens(List(id)))), _, _, _) =>
          forVals += id.range
        case Generator(_, generatorPattern, _, _, _) =>
          generatorPattern.tokens.find(_.tokenType.isId) map { token =>
            val text = token.text
            if (!text.startsWith("`") && !text(0).isUpper)
              forVals += token.range
          }
        case StatSeq(Some((selfRefExpr, _)), _, _) =>
          def findAscriptionExpr(ast: AstNode): Option[AscriptionExpr] = {
            ast match {
              case ascriptionExpression: AscriptionExpr => Some(ascriptionExpression)
              case _ =>
                for {
                  child <- ast.immediateChildren
                  ascriptionExpression <- findAscriptionExpr(child)
                } return Some(ascriptionExpression)
                None
            }
          }
          val selfRefTokenOpt = findAscriptionExpr(selfRefExpr) match {
            case Some(AscriptionExpr(left, _, _)) => left.flatMap(_.tokens).find(_.tokenType.isId).headOption
            case None => selfRefExpr.tokens.headOption
          }
          for {
            selfRefToken <- selfRefTokenOpt
            text = selfRefToken.text
            token <- astNode.tokens.filter { token => token.text == text || token.text == "`" + text + "`" }
          } maybeSelfRefs += token.range
        case Annotation(_, annotationType, _, _) =>
          for (token <- annotationType.tokens.filter { _.tokenType.isId })
            annotations += token.range
        case _ =>
      }
      astNode.immediateChildren.foreach(scan)
    }

    for ((cu, tokens) <- safeParse(source)) {
      scan(cu)
      for (token <- tokens if token.text == "classOf")
        maybeClassOfs += token.range
    }

    SyntacticInfo(namedArgs, forVals, maybeSelfRefs, maybeClassOfs, annotations)
  }

}