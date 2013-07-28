package scala.tools.eclipse.properties.syntaxcoloring

import scalariform.lexer.Tokens._
import scalariform.lexer._

object ScalaSyntaxClasses {

  val SINGLE_LINE_COMMENT = ScalaSyntaxClass("Single-line comment", "syntaxColoring.singleLineComment")
  val MULTI_LINE_COMMENT = ScalaSyntaxClass("Multi-line comment", "syntaxColoring.multiLineComment")
  val SCALADOC = ScalaSyntaxClass("Scaladoc comment", "syntaxColoring.scaladoc")
  val SCALADOC_CODE_BLOCK = ScalaSyntaxClass("Scaladoc code block", "syntaxColoring.scaladocCodeBlock")
  val SCALADOC_ANNOTATION = ScalaSyntaxClass("Scaladoc annotation", "syntaxColoring.scaladocAnnotation")
  val SCALADOC_MACRO = ScalaSyntaxClass("Scaladoc macro", "syntaxColoring.scaladocMacro")
  val TASK_TAG = ScalaSyntaxClass("Task Tag", "syntaxColoring.taskTag")
  val OPERATOR = ScalaSyntaxClass("Operator", "syntaxColoring.operator")
  val KEYWORD = ScalaSyntaxClass("Keywords (excluding 'return')", "syntaxColoring.keyword")
  val RETURN = ScalaSyntaxClass("Keyword 'return'", "syntaxColoring.return")
  val STRING = ScalaSyntaxClass("Strings", "syntaxColoring.string")
  val CHARACTER = ScalaSyntaxClass("Characters", "syntaxColoring.character")
  val MULTI_LINE_STRING = ScalaSyntaxClass("Multi-line string", "syntaxColoring.multiLineString")
  val BRACKET = ScalaSyntaxClass("Brackets", "syntaxColoring.bracket")
  val DEFAULT = ScalaSyntaxClass("Others", "syntaxColoring.default")
  val SYMBOL = ScalaSyntaxClass("Symbol", "syntaxColoring.symbol")
  val NUMBER_LITERAL = ScalaSyntaxClass("Number literals", "syntaxColoring.numberLiteral")
  val ESCAPE_SEQUENCE = ScalaSyntaxClass("Escape sequences", "syntaxColoring.escapeSequence")

  val XML_COMMENT = ScalaSyntaxClass("Comments", "syntaxColoring.xml.comment")
  val XML_ATTRIBUTE_VALUE = ScalaSyntaxClass("Attribute values", "syntaxColoring.xml.attributeValue")
  val XML_ATTRIBUTE_NAME = ScalaSyntaxClass("Attribute names", "syntaxColoring.xml.attributeName")
  val XML_ATTRIBUTE_EQUALS = ScalaSyntaxClass("Attribute equal signs", "syntaxColoring.xml.equals")
  val XML_TAG_DELIMITER = ScalaSyntaxClass("Tag delimiters", "syntaxColoring.xml.tagDelimiter")
  val XML_TAG_NAME = ScalaSyntaxClass("Tag names", "syntaxColoring.xml.tagName")
  val XML_PI = ScalaSyntaxClass("Processing instructions", "syntaxColoring.xml.processingInstruction")
  val XML_CDATA_BORDER = ScalaSyntaxClass("CDATA delimiters", "syntaxColoring.xml.cdata")

  val ANNOTATION = ScalaSyntaxClass("Annotation", "syntaxColoring.semantic.annotation", canBeDisabled = true)
  val CASE_CLASS = ScalaSyntaxClass("Case class", "syntaxColoring.semantic.caseClass", canBeDisabled = true)
  val CASE_OBJECT = ScalaSyntaxClass("Case object", "syntaxColoring.semantic.caseObject", canBeDisabled = true)
  val CLASS = ScalaSyntaxClass("Class", "syntaxColoring.semantic.class", canBeDisabled = true)
  val LAZY_LOCAL_VAL = ScalaSyntaxClass("Lazy local val", "syntaxColoring.semantic.lazyLocalVal", canBeDisabled = true)
  val LAZY_TEMPLATE_VAL = ScalaSyntaxClass("Lazy template val", "syntaxColoring.semantic.lazyTemplateVal", canBeDisabled = true)
  val LOCAL_VAL = ScalaSyntaxClass("Local val", "syntaxColoring.semantic.localVal", canBeDisabled = true)
  val LOCAL_VAR = ScalaSyntaxClass("Local var", "syntaxColoring.semantic.localVar", canBeDisabled = true)
  val METHOD = ScalaSyntaxClass("Method", "syntaxColoring.semantic.method", canBeDisabled = true)
  val OBJECT = ScalaSyntaxClass("Object", "syntaxColoring.semantic.object", canBeDisabled = true)
  val PACKAGE = ScalaSyntaxClass("Package", "syntaxColoring.semantic.package", canBeDisabled = true)
  val PARAM = ScalaSyntaxClass("Parameter", "syntaxColoring.semantic.methodParam", canBeDisabled = true)
  val TEMPLATE_VAL = ScalaSyntaxClass("Template val", "syntaxColoring.semantic.templateVal", canBeDisabled = true)
  val TEMPLATE_VAR = ScalaSyntaxClass("Template var", "syntaxColoring.semantic.templateVar", canBeDisabled = true)
  val TRAIT = ScalaSyntaxClass("Trait", "syntaxColoring.semantic.trait", canBeDisabled = true)
  val TYPE = ScalaSyntaxClass("Type", "syntaxColoring.semantic.type", canBeDisabled = true)
  val TYPE_PARAMETER = ScalaSyntaxClass("Type parameter", "syntaxColoring.semantic.typeParameter", canBeDisabled = true)
  val IDENTIFIER_IN_INTERPOLATED_STRING = ScalaSyntaxClass("Identifier in interpolated string", "syntaxColoring.semantic.identifierInInterpolatedString", hasForegroundColor = false, canBeDisabled = true)

  case class Category(name: String, children: List[ScalaSyntaxClass])

  val scalaSyntacticCategory = Category("Scala (syntactic)", List(
    BRACKET, KEYWORD, RETURN, MULTI_LINE_STRING, OPERATOR, DEFAULT, STRING, CHARACTER, NUMBER_LITERAL, ESCAPE_SEQUENCE, SYMBOL))

  val scalaSemanticCategory = Category("Scala (semantic)", List(
    ANNOTATION, CASE_CLASS, CASE_OBJECT, CLASS, LAZY_LOCAL_VAL, LAZY_TEMPLATE_VAL,
    LOCAL_VAL, LOCAL_VAR, METHOD, OBJECT, PACKAGE, PARAM, TEMPLATE_VAL, TEMPLATE_VAR,
    TRAIT, TYPE, TYPE_PARAMETER, IDENTIFIER_IN_INTERPOLATED_STRING))

  val commentsCategory = Category("Comments", List(
    SINGLE_LINE_COMMENT, MULTI_LINE_COMMENT, SCALADOC, SCALADOC_CODE_BLOCK, SCALADOC_ANNOTATION, SCALADOC_MACRO, TASK_TAG))

  val xmlCategory = Category("XML", List(
    XML_ATTRIBUTE_NAME, XML_ATTRIBUTE_VALUE, XML_ATTRIBUTE_EQUALS, XML_CDATA_BORDER, XML_COMMENT, XML_TAG_DELIMITER,
    XML_TAG_NAME, XML_PI))

  val categories = List(scalaSyntacticCategory, scalaSemanticCategory, commentsCategory, xmlCategory)

  val ALL_SYNTAX_CLASSES = categories.flatMap(_.children)

  val ENABLED_SUFFIX = ".enabled"
  val FOREGROUND_COLOR_SUFFIX = ".color"
  val BACKGROUND_COLOR_SUFFIX = ".backgroundColor"
  val BACKGROUND_COLOR_ENABLED_SUFFIX = ".backgroundColorEnabled"
  val BOLD_SUFFIX = ".bold"
  val ITALIC_SUFFIX = ".italic"
  val UNDERLINE_SUFFIX = ".underline"

  val ENABLE_SEMANTIC_HIGHLIGHTING = "syntaxColoring.semantic.enabled"

  val USE_SYNTACTIC_HINTS = "syntaxColoring.semantic.useSyntacticHints"

  val STRIKETHROUGH_DEPRECATED = "syntaxColoring.semantic.strikeDeprecated"

}

object ScalariformToSyntaxClass {

  import scala.tools.eclipse.properties.syntaxcoloring.{ ScalaSyntaxClasses => ssc }

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