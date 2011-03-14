package scala.tools.eclipse.properties
import org.eclipse.jdt.ui.PreferenceConstants._
import org.eclipse.jdt.ui.text.IJavaColorConstants._

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