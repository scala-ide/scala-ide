package scala.tools.eclipse.properties.syntaxcolouring

import scalariform.lexer.Tokens._
import scalariform.lexer._

object ScalaSyntaxClasses {

  val SINGLE_LINE_COMMENT = ScalaSyntaxClass("Single-line comment", "syntaxColouring.singleLineComment")
  val MULTI_LINE_COMMENT = ScalaSyntaxClass("Multi-line comment", "syntaxColouring.multiLineComment")
  val SCALADOC = ScalaSyntaxClass("Scaladoc comment", "syntaxColouring.scaladoc")
  val OPERATOR = ScalaSyntaxClass("Operator", "syntaxColouring.operator")
  val KEYWORD = ScalaSyntaxClass("Keywords (excluding 'return')", "syntaxColouring.keyword")
  val RETURN = ScalaSyntaxClass("Keyword 'return'", "syntaxColouring.return")
  val STRING = ScalaSyntaxClass("Strings", "syntaxColouring.string")
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
    BRACKET, KEYWORD, RETURN, MULTI_LINE_STRING, OPERATOR, DEFAULT, STRING, NUMBER_LITERAL, ESCAPE_SEQUENCE, SYMBOL))

  val scalaSemanticCategory = Category("Scala (semantic)", List(
    ANNOTATION, CASE_CLASS, CASE_OBJECT, CLASS, LAZY_LOCAL_VAL, LAZY_TEMPLATE_VAL,
    LOCAL_VAL, LOCAL_VAR, METHOD, OBJECT, PACKAGE, PARAM, TEMPLATE_VAL, TEMPLATE_VAR,
    TRAIT, TYPE, TYPE_PARAMETER))

  val commentsCategory = Category("Comments", List(
    SINGLE_LINE_COMMENT, MULTI_LINE_COMMENT, SCALADOC))

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

  val ALL_SUFFIXES = List(ENABLED_SUFFIX, FOREGROUND_COLOUR_SUFFIX, BACKGROUND_COLOUR_SUFFIX,
    BACKGROUND_COLOUR_ENABLED_SUFFIX, BOLD_SUFFIX, ITALIC_SUFFIX, UNDERLINE_SUFFIX)

  val ALL_KEYS = (for {
    syntaxClass <- ALL_SYNTAX_CLASSES
    suffix <- ALL_SUFFIXES
  } yield syntaxClass.baseName + suffix).toSet

  val ENABLE_SEMANTIC_HIGHLIGHTING = "syntaxColouring.semantic.enabled"
    
  val USE_SYNTACTIC_HINTS = "syntaxColouring.semantic.useSyntacticHints"

  val STRIKETHROUGH_DEPRECATED = "syntaxColouring.semantic.strikeDeprecated"

}

object ScalariformToSyntaxClass {

  // TODO: Distinguish inside from outside of CDATA; distinguish XML tag and attribute name

  def apply(token: Token): ScalaSyntaxClass = token.tokenType match {
    case LPAREN | RPAREN | LBRACE | RBRACE | LBRACKET | RBRACKET => ScalaSyntaxClasses.BRACKET
    case STRING_LITERAL => ScalaSyntaxClasses.STRING
    case TRUE | FALSE | NULL => ScalaSyntaxClasses.KEYWORD
    case RETURN => ScalaSyntaxClasses.RETURN
    case t if t.isKeyword => ScalaSyntaxClasses.KEYWORD
    case LINE_COMMENT => ScalaSyntaxClasses.SINGLE_LINE_COMMENT
    case MULTILINE_COMMENT if token.isScalaDocComment => ScalaSyntaxClasses.SCALADOC
    case MULTILINE_COMMENT => ScalaSyntaxClasses.MULTI_LINE_COMMENT
    case PLUS | MINUS | STAR | PIPE | TILDE | EXCLAMATION => ScalaSyntaxClasses.OPERATOR
    case DOT | COMMA | COLON | USCORE | EQUALS | SEMI |
      LARROW | ARROW | SUBTYPE | SUPERTYPE | VIEWBOUND => ScalaSyntaxClasses.OPERATOR
    case VARID if Chars.isOperatorPart(token.text(0)) => ScalaSyntaxClasses.OPERATOR
    case FLOATING_POINT_LITERAL | INTEGER_LITERAL => ScalaSyntaxClasses.NUMBER_LITERAL
    case SYMBOL_LITERAL => ScalaSyntaxClasses.SYMBOL
    case XML_START_OPEN | XML_EMPTY_CLOSE | XML_TAG_CLOSE | XML_END_OPEN => ScalaSyntaxClasses.XML_TAG_DELIMITER
    case XML_NAME => ScalaSyntaxClasses.XML_TAG_NAME
    case XML_ATTR_EQ => ScalaSyntaxClasses.XML_ATTRIBUTE_EQUALS
    case XML_PROCESSING_INSTRUCTION => ScalaSyntaxClasses.XML_PI
    case XML_COMMENT => ScalaSyntaxClasses.XML_COMMENT
    case XML_ATTR_VALUE => ScalaSyntaxClasses.XML_ATTRIBUTE_VALUE
    case XML_CDATA => ScalaSyntaxClasses.XML_CDATA_BORDER
    case XML_UNPARSED | XML_WHITESPACE | XML_PCDATA | VARID | _ => ScalaSyntaxClasses.DEFAULT
  }

}