package scala.tools.eclipse.properties.syntaxcoloring

import scala.tools.eclipse.properties.syntaxcoloring.ScalaSyntaxClasses._
import scalariform.lexer.ScalaLexer
import scala.tools.eclipse.semantichighlighting.classifier.SymbolTypes
import scala.tools.eclipse.semantichighlighting.Position

object SyntaxColoringPreviewText {

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

  case class ColoringInfo(symbolType: SymbolTypes.SymbolType, deprecated: Boolean = false, inInterpolatedString: Boolean = false)

  import SymbolTypes._
  private val identifierToSyntaxClass: Map[String, ColoringInfo] = Map(
    "foo" -> ColoringInfo(Package),
    "bar" -> ColoringInfo(Package),
    "baz" -> ColoringInfo(Package),
    "Annotation" -> ColoringInfo(Annotation),
    "Class" -> ColoringInfo(Class),
    "CaseClass" -> ColoringInfo(CaseClass),
    "CaseObject" -> ColoringInfo(CaseObject),
    "Trait" -> ColoringInfo(Trait),
    "Int" -> ColoringInfo(Class),
    "method" -> ColoringInfo(Method),
    "param" -> ColoringInfo(Param),
    "lazyLocalVal" -> ColoringInfo(LazyLocalVal),
    "localVal" -> ColoringInfo(LocalVal),
    "localVar" -> ColoringInfo(LocalVar),
    "lazyTemplateVal" -> ColoringInfo(LazyTemplateVal),
    "templateVal" -> ColoringInfo(TemplateVal),
    "templateVar" -> ColoringInfo(TemplateVar),
    "T" -> ColoringInfo(TypeParameter),
    "Type" -> ColoringInfo(Type),
    "Object" -> ColoringInfo(Object),
    "sym" -> ColoringInfo(LocalVal),
    "deprecated" -> ColoringInfo(Annotation),
    "deprecatedMethod" -> ColoringInfo(Method, deprecated = true),
    "str" -> ColoringInfo(TemplateVal),
    "p\u0430ram" -> ColoringInfo(Param, inInterpolatedString = true),
    "templateV\u0430l" -> ColoringInfo(TemplateVal, inInterpolatedString = true),
    "templateV\u0430r" -> ColoringInfo(TemplateVar, inInterpolatedString = true)
    )

  val semanticLocations: List[Position] =
    for {
      token <- ScalaLexer.rawTokenise(previewText, forgiveErrors = true)
      if token.tokenType.isId
      ColoringInfo(symbolType, deprecated, inStringInterpolation) <- identifierToSyntaxClass get token.text
    } yield new Position(token.offset, token.length, symbolType, deprecated, inStringInterpolation)

}