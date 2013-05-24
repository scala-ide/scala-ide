package scala.tools.eclipse.semantichighlighting.classifier

import scala.tools.eclipse.util.CollectionUtil
import scalariform.lexer.{ScalaLexer, Token}
import scalariform.parser._
import scalariform.utils.Range
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.IRegion
import scala.tools.eclipse.util.parsing.ScalariformParser

// Symbol information derived by purely syntactic means, via Scalariform's parser, because it (appears) 
// difficult to get this out scalac trees
case class SyntacticInfo(
  namedArgs: Set[IRegion],
  forVals: Set[IRegion],
  maybeSelfRefs: Set[IRegion],
  maybeClassOfs: Set[IRegion],
  annotations: Set[IRegion], 
  packages: Set[IRegion],
  identifiersInStringInterpolations: Set[IRegion]
)

object SyntacticInfo {

  private class RangeOps(range: Range) {
    def toRegion: IRegion = new Region(range.offset, range.length)
  }

  private implicit def range2Region(range: Range): RangeOps = new RangeOps(range)

  def noSyntacticInfo = SyntacticInfo(Set(), Set(), Set(), Set(), Set(), Set(), Set())
  
  def getSyntacticInfo(source: String): SyntacticInfo = {
    var namedArgs: Set[IRegion] = Set()
    var forVals: Set[IRegion] = Set()
    var maybeSelfRefs: Set[IRegion] = Set()
    var maybeClassOfs: Set[IRegion] = Set()
    var annotations: Set[IRegion] = Set()
    var packages: Set[IRegion] = Set()
    var identifiersInStringInterpolations: Set[IRegion] = Set()

    def scan(astNode: AstNode) {
      astNode match {
        case Argument(Expr(List(EqualsExpr(List(CallExpr(None, id, None, Nil, None)), _, _)))) =>
          namedArgs += id.range.toRegion
        case Generator(_, Expr(List(GeneralTokens(List(id)))), _, _, _) =>
          forVals += id.range.toRegion
        case Generator(_, generatorPattern, _, _, _) =>
          generatorPattern.tokens.find(_.tokenType.isId) map { token =>
            val text = token.text
            if (!text.startsWith("`") && !text(0).isUpper)
              forVals += token.range.toRegion
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
          } maybeSelfRefs += token.range.toRegion
        case ann @ Annotation(_, annotationType, _, _) =>
          val tokens = annotationType.tokens.filter(_.tokenType.isId)
          val (pkges, annotation) = CollectionUtil.splitAtLast(tokens)
          pkges.foreach(packages += _.range.toRegion)
          annotation foreach ( annotations += _.range.toRegion)
        case StringInterpolation(_, stringPartsAndScala, _) =>
          for ((_, expr) <- stringPartsAndScala) {
            val identifiers = expr.tokens.filter(_.tokenType.isId)
            identifiersInStringInterpolations ++= identifiers.map(_.range.toRegion)
          }
        case _ =>
      }
      astNode.immediateChildren.foreach(scan)
    }

    for ((cu, tokens) <- ScalariformParser.safeParse(source)) {
      scan(cu)
      for (token <- tokens if token.text == "classOf")
        maybeClassOfs += token.range.toRegion
    }

    SyntacticInfo(namedArgs, forVals, maybeSelfRefs, maybeClassOfs, annotations, packages, identifiersInStringInterpolations)
  }
}