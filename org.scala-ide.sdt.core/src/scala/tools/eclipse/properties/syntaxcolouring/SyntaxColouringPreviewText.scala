package scala.tools.eclipse.properties.syntaxcolouring

import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses._
import scalariform.lexer.ScalaLexer
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes
import scala.tools.eclipse.semantichighlighting.Position

object SyntaxColouringPreviewText {

  val previewText = """package foo.bar.baz
/**
 * Scaladoc
 * @scaladocAnnotation value
 * $SCALADOC_MACRO
 * {{{
 * @annotation.tailrec
 * def f(i: Int): Int =
 *   if (i > 0) f(i - 1) else 0
 * }}}
 */
@Annotation
class Class[T] extends Trait {
  object Object
  case object CaseObject
  case class CaseClass
  type Type = Int
  lazy val lazyTemplateVal = 42
  val templateVal = 42
  var templateVar = 24
  def method(param: Int): Int = {
    // Single-line comment
    /* Multi-line comment */
    lazy val lazyLocalVal = 42
    val localVal = "foo\nbar" + """ + "\"\"\"" + "multiline string" + "\"\"\"" + """
    var localVar =
      <tag attributeName="value">
        <!-- XML comment -->
        <?processinginstruction?>
        <![CDATA[ CDATA ]]>
        PCDATA
      </tag>
    val sym = 'symbol
    return 42
  }
  @deprecated def deprecatedMethod(param: Int) = ???
  templateVar = deprecatedMethod(12)
  val str = s"Here is a $templateV\u0430l, " +
    s"$templateV\u0430r, $p\u0430ram, $$notAVariable"
}"""

  case class ColouringInfo(symbolType: SymbolTypes.SymbolType, deprecated: Boolean = false, inInterpolatedString: Boolean = false)

  import SymbolTypes._
  private val identifierToSyntaxClass: Map[String, ColouringInfo] = Map(
    "foo" -> ColouringInfo(Package),
    "bar" -> ColouringInfo(Package),
    "baz" -> ColouringInfo(Package),
    "Annotation" -> ColouringInfo(Annotation),
    "Class" -> ColouringInfo(Class),
    "CaseClass" -> ColouringInfo(CaseClass),
    "CaseObject" -> ColouringInfo(CaseObject),
    "Trait" -> ColouringInfo(Trait),
    "Int" -> ColouringInfo(Class),
    "method" -> ColouringInfo(Method),
    "param" -> ColouringInfo(Param),
    "lazyLocalVal" -> ColouringInfo(LazyLocalVal),
    "localVal" -> ColouringInfo(LocalVal),
    "localVar" -> ColouringInfo(LocalVar),
    "lazyTemplateVal" -> ColouringInfo(LazyTemplateVal),
    "templateVal" -> ColouringInfo(TemplateVal),
    "templateVar" -> ColouringInfo(TemplateVar),
    "T" -> ColouringInfo(TypeParameter),
    "Type" -> ColouringInfo(Type),
    "Object" -> ColouringInfo(Object),
    "sym" -> ColouringInfo(LocalVal),
    "deprecated" -> ColouringInfo(Annotation),
    "deprecatedMethod" -> ColouringInfo(Method, deprecated = true),
    "str" -> ColouringInfo(TemplateVal),
    "p\u0430ram" -> ColouringInfo(Param, inInterpolatedString = true),
    "templateV\u0430l" -> ColouringInfo(TemplateVal, inInterpolatedString = true),
    "templateV\u0430r" -> ColouringInfo(TemplateVar, inInterpolatedString = true)
    )

  val semanticLocations: List[Position] =
    for {
      token <- ScalaLexer.rawTokenise(previewText, forgiveErrors = true)
      if token.tokenType.isId
      ColouringInfo(symbolType, deprecated, inStringInterpolation) <- identifierToSyntaxClass get token.text
    } yield new Position(token.offset, token.length, symbolType, deprecated, inStringInterpolation)

}