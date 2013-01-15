package scala.tools.eclipse.properties.syntaxcolouring

import scala.tools.eclipse.properties.syntaxcolouring.ScalaSyntaxClasses._
import scalariform.lexer.ScalaLexer

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
}"""

  case class ColouringLocation(syntaxClass: ScalaSyntaxClass, offset: Int, length: Int)

  private val identifierToSyntaxClass: Map[String, ScalaSyntaxClass] = Map(
    "foo" -> PACKAGE,
    "bar" -> PACKAGE,
    "baz" -> PACKAGE,
    "Annotation" -> ANNOTATION,
    "Class" -> CLASS,
    "CaseClass" -> CASE_CLASS,
    "CaseObject" -> CASE_OBJECT,
    "Trait" -> TRAIT,
    "Int" -> CLASS,
    "method" -> METHOD,
    "param" -> PARAM,
    "lazyLocalVal" -> LAZY_LOCAL_VAL,
    "localVal" -> LOCAL_VAL,
    "localVar" -> LOCAL_VAR,
    "lazyTemplateVal" -> LAZY_TEMPLATE_VAL,
    "templateVal" -> TEMPLATE_VAL,
    "templateVar" -> TEMPLATE_VAR,
    "T" -> TYPE_PARAMETER,
    "Type" -> TYPE,
    "Object" -> OBJECT,
    "sym" -> LOCAL_VAL)

  val semanticLocations: List[ColouringLocation] =
    for {
      token <- ScalaLexer.rawTokenise(previewText, forgiveErrors = true)
      if token.tokenType.isId
      syntaxClass <- identifierToSyntaxClass get token.text
    } yield ColouringLocation(syntaxClass, token.offset, token.length)

}