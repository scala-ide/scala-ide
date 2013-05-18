package scala.tools.eclipse.properties.syntaxcolouring

import scalariform.lexer.Tokens._
import scalariform.lexer._

object ScalaSyntaxClasses {

  val SINGLE_LINE_COMMENT = ScalaSyntaxClass("Single-line comment", "syntaxColouring.singleLineComment")
  val MULTI_LINE_COMMENT = ScalaSyntaxClass("Multi-line comment", "syntaxColouring.multiLineComment")
  val SCALADOC = ScalaSyntaxClass("Scaladoc comment", "syntaxColouring.scaladoc")
  val SCALADOC_CODE_BLOCK = ScalaSyntaxClass("Scaladoc code block", "syntaxColouring.scaladocCodeBlock")
  val SCALADOC_ANNOTATION = ScalaSyntaxClass("Scaladoc annotation", "syntaxColouring.scaladocAnnotation")
  val SCALADOC_MACRO = ScalaSyntaxClass("Scaladoc macro", "syntaxColouring.scaladocMacro")
  val TASK_TAG = ScalaSyntaxClass("Task Tag", "syntaxColouring.taskTag")
  val OPERATOR = ScalaSyntaxClass("Operator", "syntaxColouring.operator")
  val KEYWORD = ScalaSyntaxClass("Keywords (excluding 'return')", "syntaxColouring.keyword")
  val RETURN = ScalaSyntaxClass("Keyword 'return'", "syntaxColouring.return")
  val STRING = ScalaSyntaxClass("Strings", "syntaxColouring.string")
  val CHARACTER = ScalaSyntaxClass("Characters", "syntaxColouring.character")
  val MULTI_LINE_STRING = ScalaSyntaxClass("Multi-line string", "syntaxColouring.multiLineString")
  val BRACKET = ScalaSyntaxClass("Brackets", "syntaxColouring.bracket")
  val DEFAULT = ScalaSyntaxClass("Others", "syntaxColouring.default")
  val SYMBOL = ScalaSyntaxClass("Symbol", "syntaxColouring.symbol")
  val NUMBER_LITERAL = ScalaSyntaxClass("Number literals", "syntaxColouring.numberLiteral")
  val ESCAPE_SEQUENCE = ScalaSyntaxClass("Escape sequences", "syntaxColouring.escapeSequence")

  val XML_COMMENT = ScalaSyntaxClass("Comments", "syntaxColouring.xml.comment")
  val XML_ATTRIBUTE_VALUE = ScalaSyntaxClass("Attribute values", "syntaxColouring.xml.attributeValue")
  val XML_ATTRIBUTE_NAME = ScalaSyntaxClass("Attribute names", "syntaxColouring.xml.attributeName")
  val XML_ATTRIBUTE_EQUALS = ScalaSyntaxClass("Attribute equal signs", "syntaxColouring.xml.equals")
  val XML_TAG_DELIMITER = ScalaSyntaxClass("Tag delimiters", "syntaxColouring.xml.tagDelimiter")
  val XML_TAG_NAME = ScalaSyntaxClass("Tag names", "syntaxColouring.xml.tagName")
  val XML_PI = ScalaSyntaxClass("Processing instructions", "syntaxColouring.xml.processingInstruction")
  val XML_CDATA_BORDER = ScalaSyntaxClass("CDATA delimiters", "syntaxColouring.xml.cdata")

  val ANNOTATION = ScalaSyntaxClass("Annotation", "syntaxColouring.semantic.annotation", canBeDisabled = true)
  val CASE_CLASS = ScalaSyntaxClass("Case class", "syntaxColouring.semantic.caseClass", canBeDisabled = true)
  val CASE_OBJECT = ScalaSyntaxClass("Case object", "syntaxColouring.semantic.caseObject", canBeDisabled = true)
  val CLASS = ScalaSyntaxClass("Class", "syntaxColouring.semantic.class", canBeDisabled = true)
  val LAZY_LOCAL_VAL = ScalaSyntaxClass("Lazy local val", "syntaxColouring.semantic.lazyLocalVal", canBeDisabled = true)
  val LAZY_TEMPLATE_VAL = ScalaSyntaxClass("Lazy template val", "syntaxColouring.semantic.lazyTemplateVal", canBeDisabled = true)
  val LOCAL_VAL = ScalaSyntaxClass("Local val", "syntaxColouring.semantic.localVal", canBeDisabled = true)
  val LOCAL_VAR = ScalaSyntaxClass("Local var", "syntaxColouring.semantic.localVar", canBeDisabled = true)
  val METHOD = ScalaSyntaxClass("Method", "syntaxColouring.semantic.method", canBeDisabled = true)
  val OBJECT = ScalaSyntaxClass("Object", "syntaxColouring.semantic.object", canBeDisabled = true)
  val PACKAGE = ScalaSyntaxClass("Package", "syntaxColouring.semantic.package", canBeDisabled = true)
  val PARAM = ScalaSyntaxClass("Parameter", "syntaxColouring.semantic.methodParam", canBeDisabled = true)
  val TEMPLATE_VAL = ScalaSyntaxClass("Template val", "syntaxColouring.semantic.templateVal", canBeDisabled = true)
  val TEMPLATE_VAR = ScalaSyntaxClass("Template var", "syntaxColouring.semantic.templateVar", canBeDisabled = true)
  val TRAIT = ScalaSyntaxClass("Trait", "syntaxColouring.semantic.trait", canBeDisabled = true)
  val TYPE = ScalaSyntaxClass("Type", "syntaxColouring.semantic.type", canBeDisabled = true)
  val TYPE_PARAMETER = ScalaSyntaxClass("Type parameter", "syntaxColouring.semantic.typeParameter", canBeDisabled = true)

  case class Category(name: String, children: List[ScalaSyntaxClass])

  val scalaSyntacticCategory = Category("Scala (syntactic)", List(
    BRACKET, KEYWORD, RETURN, MULTI_LINE_STRING, OPERATOR, DEFAULT, STRING, CHARACTER, NUMBER_LITERAL, ESCAPE_SEQUENCE, SYMBOL))

  val scalaSemanticCategory = Category("Scala (semantic)", List(
    ANNOTATION, CASE_CLASS, CASE_OBJECT, CLASS, LAZY_LOCAL_VAL, LAZY_TEMPLATE_VAL,
    LOCAL_VAL, LOCAL_VAR, METHOD, OBJECT, PACKAGE, PARAM, TEMPLATE_VAL, TEMPLATE_VAR,
    TRAIT, TYPE, TYPE_PARAMETER))

  val commentsCategory = Category("Comments", List(
    SINGLE_LINE_COMMENT, MULTI_LINE_COMMENT, SCALADOC, SCALADOC_CODE_BLOCK, SCALADOC_ANNOTATION, SCALADOC_MACRO, TASK_TAG))

  val xmlCategory = Category("XML", List(
    XML_ATTRIBUTE_NAME, XML_ATTRIBUTE_VALUE, XML_ATTRIBUTE_EQUALS, XML_CDATA_BORDER, XML_COMMENT, XML_TAG_DELIMITER,
    XML_TAG_NAME, XML_PI))

  val categories = List(scalaSyntacticCategory, scalaSemanticCategory, commentsCategory, xmlCategory)

  val ALL_SYNTAX_CLASSES = categories.flatMap(_.children)

  val ENABLED_SUFFIX = ".enabled"
  val FOREGROUND_COLOUR_SUFFIX = ".colour"
  val BACKGROUND_COLOUR_SUFFIX = ".backgroundColour"
  val BACKGROUND_COLOUR_ENABLED_SUFFIX = ".backgroundColourEnabled"
  val BOLD_SUFFIX = ".bold"
  val ITALIC_SUFFIX = ".italic"
  val UNDERLINE_SUFFIX = ".underline"

  val ENABLE_SEMANTIC_HIGHLIGHTING = "syntaxColouring.semantic.enabled"
    
  val USE_SYNTACTIC_HINTS = "syntaxColouring.semantic.useSyntacticHints"

  val STRIKETHROUGH_DEPRECATED = "syntaxColouring.semantic.strikeDeprecated"

}

object ScalariformToSyntaxClass {

  import scala.tools.eclipse.properties.syntaxcolouring.{ ScalaSyntaxClasses => ssc }

  // TODO: Distinguish inside from outside of CDATA; distinguish XML tag and attribute name

  /**
   * If one wants to tokenize source code by Scalariform, one probably also needs to translate the
   * token to a format the UI-Classes of Eclipse can understand. If this the case than this method
   * should be used.
   *
   * Because Scalariform does not treat all token the way the IDE needs them, for some of them they
   * are replaced with a different kind of token.
   */
  def apply(token: Token): ScalaSyntaxClass = token.tokenType match {
    case LPAREN | RPAREN | LBRACE | RBRACE | LBRACKET | RBRACKET         => ssc.BRACKET
    case STRING_LITERAL                                                  => ssc.STRING
    case TRUE | FALSE | NULL                                             => ssc.KEYWORD
    case RETURN                                                          => ssc.RETURN
    case t if t.isKeyword                                                => ssc.KEYWORD
    case LINE_COMMENT                                                    => ssc.SINGLE_LINE_COMMENT
    case MULTILINE_COMMENT if token.isScalaDocComment                    => ssc.SCALADOC
    case MULTILINE_COMMENT                                               => ssc.MULTI_LINE_COMMENT
    case PLUS | MINUS | STAR | PIPE | TILDE | EXCLAMATION                => ssc.OPERATOR
    case DOT | COMMA | COLON | USCORE | EQUALS | SEMI | LARROW |
         ARROW | SUBTYPE | SUPERTYPE | VIEWBOUND | AT | HASH             => ssc.OPERATOR
    case VARID if Chars.isOperatorPart(token.text(0))                    => ssc.OPERATOR
    case FLOATING_POINT_LITERAL | INTEGER_LITERAL                        => ssc.NUMBER_LITERAL
    case SYMBOL_LITERAL                                                  => ssc.SYMBOL
    case XML_START_OPEN | XML_EMPTY_CLOSE | XML_TAG_CLOSE | XML_END_OPEN => ssc.XML_TAG_DELIMITER
    case XML_NAME                                                        => ssc.XML_TAG_NAME
    case XML_ATTR_EQ                                                     => ssc.XML_ATTRIBUTE_EQUALS
    case XML_PROCESSING_INSTRUCTION                                      => ssc.XML_PI
    case XML_COMMENT                                                     => ssc.XML_COMMENT
    case XML_ATTR_VALUE                                                  => ssc.XML_ATTRIBUTE_VALUE
    case XML_CDATA                                                       => ssc.XML_CDATA_BORDER
    case XML_UNPARSED | XML_WHITESPACE | XML_PCDATA | VARID | _          => ssc.DEFAULT
  }

}