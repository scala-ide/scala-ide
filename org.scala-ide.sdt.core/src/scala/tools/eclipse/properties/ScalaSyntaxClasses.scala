package scala.tools.eclipse.properties

import org.eclipse.jdt.ui.PreferenceConstants._
import org.eclipse.jdt.ui.text.IJavaColorConstants._
import scalariform.lexer._
import scalariform.lexer.Tokens._

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

  val XML_COMMENT = ScalaSyntaxClass("Comments", "syntaxColouring.xml.comment")
  val XML_ATTRIBUTE_VALUE = ScalaSyntaxClass("Attribute values", "syntaxColouring.xml.attributeValue")
  val XML_ATTRIBUTE_NAME = ScalaSyntaxClass("Attribute names", "syntaxColouring.xml.attributeName")
  val XML_ATTRIBUTE_EQUALS = ScalaSyntaxClass("Attribute equal signs", "syntaxColouring.xml.equals")
  val XML_TAG_DELIMITER = ScalaSyntaxClass("Tag delimiters", "syntaxColouring.xml.tagDelimiter")
  val XML_TAG_NAME = ScalaSyntaxClass("Tag names", "syntaxColouring.xml.tagName")
  val XML_PI = ScalaSyntaxClass("Processing instructions", "syntaxColouring.xml.processingInstruction")
  val XML_CDATA_BORDER = ScalaSyntaxClass("CDATA delimiters", "syntaxColouring.xml.cdata")

  val ALL_SYNTAX_CLASSES = List(SINGLE_LINE_COMMENT, MULTI_LINE_COMMENT, SCALADOC, OPERATOR, KEYWORD, RETURN,
    STRING, MULTI_LINE_STRING, BRACKET, DEFAULT, XML_ATTRIBUTE_NAME, XML_ATTRIBUTE_VALUE, XML_ATTRIBUTE_EQUALS,
    XML_CDATA_BORDER, XML_COMMENT, XML_TAG_DELIMITER, XML_TAG_NAME, XML_PI)

  val COLOUR_SUFFIX = ".colour"
  val BOLD_SUFFIX = ".bold"
  val ITALIC_SUFFIX = ".italic"
  val STRIKETHROUGH_SUFFIX = ".strikethrough"
  val UNDERLINE_SUFFIX = ".underline"

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
    case DOT | COMMA | COLON | USCORE | EQUALS | SEMI | LARROW | ARROW | SUBTYPE | SUPERTYPE | VIEWBOUND => ScalaSyntaxClasses.OPERATOR
    case VARID if Chars.isOperatorPart(token.getText(0)) => ScalaSyntaxClasses.OPERATOR
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