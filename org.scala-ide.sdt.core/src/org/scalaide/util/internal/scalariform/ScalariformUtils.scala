package org.scalaide.util.internal.scalariform

import scalariform.parser._
import scalariform.lexer._
import scalariform.lexer.Tokens._
import scala.collection.mutable

object ScalariformUtils {

  def toStream(cu: AstNode): Stream[AstNode] = {
    def nodeAndChildren(node: AstNode): Stream[AstNode] = node #:: node.immediateChildren.toStream.flatMap(nodeAndChildren)
    nodeAndChildren(cu)
  }

  def toListDepthFirst(cu: AstNode): List[AstNode] = {
    val nodes = mutable.ListBuffer[(AstNode, Int)]()

    def addAll(node: AstNode, depth: Int) {
      nodes.append((node, depth))
      for (n <- node.immediateChildren) {
        addAll(n, depth + 1)
      }
    }
    addAll(cu, 0)

    nodes.sortBy(_._2).reverse.map(_._1).toList
  }

  /*
   * get parameter types and try to give them names too
   * it will do that if the method is invoked with a named parameter
   * eg: method(someString = "a")
   * or if you are passing in an unqualified identifier without invoking anything on it
   * eg: method(someString), or method(someMethod)
   * this will not name literals, unknownMethod("a") is just named "arg"
   * it will also not name compound statements, eg method(someString.length) will be named "arg"
   * warning: does NOT give back unique names, you must do that yourself
   */
  def getParameters(source: AstNode, methodNameOffset: Int, typeAtRange: (Int, Int) => String) = {
    def createParameter(exprContents: List[ExprElement]): (String, String) = {

      def getType(contents: List[ExprElement]): String = {
        val tokens = (for (expr <- contents) yield expr.tokens).flatten
        if (tokens.isEmpty) return "Any"

        val startToken = tokens.minBy(_.offset)
        val start = startToken.offset
        val endToken = tokens.maxBy(token => token.offset + token.length)
        val end = endToken.offset + endToken.length
        typeAtRange(start, end)
      }

      val (name, tpe) = exprContents match {
        //exprDotOpt must be None so we don't match a.unknownMethod(other.existingMethod)
        case List(CallExpr(/*exprDotOpt*/ None, id, _, _, _)) if id.tokenType.isId =>
          (id.text, getType(exprContents))

        //named parameter, eg a.method(aaa = 3, bbb = ccc)
        case List(EqualsExpr(List(CallExpr(_, name, _, _, _)), Token(EQUALS, _, _, _), Expr(List(contents)))) =>
          (name.text, getType(List(contents)))

        case _ =>
          ("arg", getType(exprContents))
      }

      (name, tpe)
    }

    val parameters: Option[List[List[(String, String)]]] = toStream(source).collectFirst {
      //infix expression, eg
      //other method "abc"
      case InfixExpr(List(CallExpr(_, _, _, _, _)), methodName, _, right) if methodName.offset == methodNameOffset =>
        for (expr <- right) yield expr match {
          //eg other method (1, "abc")
          //the (1, "abc") are in a ParenExpr
          case ParenExpr(_, contents, _) =>
            val createdParams = for (elem <- contents) yield {
              elem match {
                //eg other unknownMethod (1, "abc")
                //we would have a token for the 1 and then a comma, but createParameter wants no comma
                case GeneralTokens(tokens) => tokens.filterNot(_.tokenType == COMMA).map(token => createParameter(List(GeneralTokens(List(token)))))
                case other => List(createParameter(List(other)))
              }
            }
            createdParams.flatten

          //eg obj method (1, "aaa")(2, "bbb")
          //the (2, "bbb") are in a ParenArgumentExprs
          case ParenArgumentExprs(_, contents, _) =>
            for (arg <- contents.collect { case argument: Argument => argument })
              yield createParameter(arg.expr.contents)

          //other cases like no brackets, eg:
          //other method 3
          //other method other.toString
          case other => List(createParameter(List(other)))
        }

      //normal dot call expression, eg other.method or method()
      case CallExpr(_, id, _, newLineOptsAndArgumentExprss: List[(Option[Token], ArgumentExprs)], _) if id.offset == methodNameOffset =>
        val argumentLists = for ((_, argumentExpressions) <- newLineOptsAndArgumentExprss) yield argumentExpressions match {
          case BlockArgumentExprs(contents) =>
            contents.collect {
              case argument: Argument => argument
              case _: BlockExpr => Argument(null)
            }
          case ParenArgumentExprs(_, contents, _) => contents.collect { case argument: Argument => argument }
        }

        val parameters = for (argumentList <- argumentLists) yield for (argument <- argumentList) yield {
          val (name, tpe) = createParameter(Option(argument.expr).map(_.contents).getOrElse(Nil)) //argument.expr has been null when running some tests
          (name, tpe)
        }
        parameters
    }
    parameters.getOrElse(Nil)
  }

  def enclosingClassForMethodInvocation(sourceAst: AstNode, methodNameOffset: Int): Option[String] = {
    toListDepthFirst(sourceAst).collectFirst {
      case TmplDef(_, id, _, _, _, _, _, Some(body)) if containsMethod(body, methodNameOffset) => id.text
    }
  }

  private def containsMethod(body: AstNode, methodNameOffset: Int) = toStream(body).exists {
    case callExpr: CallExpr => callExpr.id.offset == methodNameOffset
    case _ => false
  }

  def callingOffsetAndLength(source: AstNode, callerOffset: Int): Option[MethodCallInfo] = {
    def argPosition(newLineOptsAndArgumentExprss: List[(Option[Token], ArgumentExprs)]): Option[ArgPosition] = {
      val argumentLists = newLineOptsAndArgumentExprss.map(_._2)

      for ((argumentExpressions, argumentListIndex) <- argumentLists.collect { case ParenArgumentExprs(_, contents, _) => contents }.zipWithIndex) {
        for ((argument, argumentIndex) <- argumentExpressions.collect { case arg: Argument => arg }.zipWithIndex) {
          def getInfoFromExprDotOpt(exprDotOpt: (List[scalariform.parser.ExprElement], scalariform.lexer.Token), namedParameter: Option[String]) = {
            exprDotOpt match {
              case (List(CallExpr(_, methodName, _, _, _)), _) if methodName.offset == callerOffset =>
                Some(ArgPosition(argumentListIndex, argumentIndex, namedParameter))
              case _ => None
            }
          }

          argument match {
            //calling normally on self, eg hof(unknownMethod)
            case Argument(Expr(List(CallExpr(None, name, _, _, _)))) if name.offset == callerOffset =>
              return Some(ArgPosition(argumentListIndex, argumentIndex, None))

            //calling normally on other, eg hof(other.unknownMethod)
            case Argument(Expr(List(CallExpr(Some(exprDotOpt), _, _, _, _)))) =>
              for (argPosition <- getInfoFromExprDotOpt(exprDotOpt, None)) return Some(argPosition)

            //calling with named parameter on self, eg hof(f = unknownMethod)
            case Argument(Expr(List(EqualsExpr(List(CallExpr(_, paramName, _, _, _)), _, Expr(List(CallExpr(None, methodName, _, _, _))))))) if methodName.offset == callerOffset =>
              return Some(ArgPosition(argumentListIndex, argumentIndex, Some(paramName.text)))

            //calling with named parameter on other, eg hof(f = other.unknownMethod)
            case Argument(Expr(List(EqualsExpr(List(CallExpr(_, paramName, _, _, _)), _, Expr(List(CallExpr(Some(exprDotOpt), _, _, _, _))))))) =>
              for (argPosition <- getInfoFromExprDotOpt(exprDotOpt, Some(paramName.text))) return Some(argPosition)

            case _ =>
          }
        }
      }
      None
    }
    toStream(source).collectFirst {
      case CallExpr(_, id, _, newLineOptsAndArgumentExprss: List[(Option[Token], ArgumentExprs)], _) if argPosition(newLineOptsAndArgumentExprss).isDefined => MethodCallInfo(id.offset, id.length, argPosition(newLineOptsAndArgumentExprss).get)
    }
  }

  /*
   * tells you whether the method call is an EqualsExpr without a parameter list
   * unknown = 0 //true
   * other.unknown = 0 //true
   * unknown() = 0 //false
   * other.unknown("a") = 0 //false
   */
  def isEqualsCallWithoutParameterList(ast: AstNode, methodNameOffset: Int) = {
    toStream(ast).exists(_ match {
      case EqualsExpr(List(CallExpr(_, id, _, params, _)), _, _) if id.offset == methodNameOffset => params.isEmpty
      case _ => false
    })
  }
}

case class MethodCallInfo(callerOffset: Int, methodNameLength: Int, argPosition: ArgPosition)

case class ArgPosition(argumentListIndex: Int, argumentIndex: Int, namedParameter: Option[String])
