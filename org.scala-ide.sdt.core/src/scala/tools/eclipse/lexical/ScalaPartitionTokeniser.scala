package scala.tools.eclipse.lexical

import org.eclipse.jdt.ui.text.IJavaPartitions._
import org.eclipse.jface.text._
import org.eclipse.jface.text.IDocument.DEFAULT_CONTENT_TYPE
import scala.annotation.{ switch, tailrec }
import scala.collection.mutable.{ Stack, ListBuffer }
import scala.tools.eclipse.lexical.ScalaPartitions._
import scala.xml.parsing.TokenTests

/**
 * The start index as well as the end index denotes the position BEFORE the
 * first sign of the range. This means that for an arbitrary string the following
 * is true:
 *
 * string: Hello World!
 * start : 0
 * end   : 11
 * length: end - start + 1 = 12
 *
 * If a range spans the whole content (as in the example above) the start index
 * is always 0 whereas the end index is always equal to the length of the input
 * minus 1.
 */
case class ScalaPartitionRegion(contentType: String, start: Int, end: Int) extends ITypedRegion {
  lazy val length = end - start + 1
  def containsPosition(offset: Int) = offset >= start && offset <= end
  def getType = contentType
  def getOffset = start
  def getLength = length

  def containsRange(offset: Int, length: Int) = containsPosition(offset) && containsPosition(offset + length)

  def shift(n: Int) = copy(start = start + n, end = end + n)
}

object ScalaPartitionTokeniser {

  def tokenise(text: String): List[ScalaPartitionRegion] = {
    val tokens = new ListBuffer[ScalaPartitionRegion]
    val tokeniser = new ScalaPartitionTokeniser(text)
    while (tokeniser.tokensRemain) {
      val nextToken = tokeniser.nextToken()
      if (nextToken.length > 0)
        tokens += nextToken
    }
    tokens.toList
  }

}

class ScalaPartitionTokeniser(text: String) extends TokenTests {
  import ScalaDocumentPartitioner.EOF

  private val length = text.length

  private var pos = 0

  private var previousTokenEnd = -1

  private var contentTypeOpt: Option[String] = None

  private def ch = if (pos >= length) EOF else text.charAt(pos)

  private def ch(lookahead: Int) = {
    val offset = pos + lookahead
    if (offset >= length || offset < 0)
      EOF
    else
      text.charAt(offset)
  }

  private def accept() { pos += 1 }

  private def accept(n: Int) { pos += n }

  private def setContentType(contentType: String) { contentTypeOpt = Some(contentType) }

  def tokensRemain = pos < length

  def nextToken(): ScalaPartitionRegion = {
    require(tokensRemain)

    modeStack.head match {
      case ScalaState(_) =>
        getScalaToken()
      case XmlState(_, _) =>
        getXmlToken()
      case StringInterpolationState(multiline, embeddedIdentifierNext) =>
        getStringInterpolationToken(multiline, embeddedIdentifierNext)
      case ScaladocCodeBlockState(nesting) =>
        accept(3)
        modeStack.pop()
        setContentType(SCALADOC_CODE_BLOCK)
        getCodeBlockComment(nesting)
      case ScaladocState(nesting) =>
        modeStack.pop()
        getMultiLineComment(nesting)
        setContentType(JAVA_DOC)
    }

    val contentType = contentTypeOpt.get
    val tokenStart = previousTokenEnd + 1
    val tokenEnd = pos - 1
    previousTokenEnd = pos - 1
    contentTypeOpt = None
    ScalaPartitionRegion(contentType, tokenStart, tokenEnd)
  }

  private def getScalaToken() {
    (ch: @switch) match {
      case EOF => require(false)
      case '<' => ch(-1) match {
        case EOF | ' ' | '\t' | '\n' | '{' | '(' | '>' if (isNameStart(ch(1)) || ch(1) == '!' || ch(1) == '?') =>
          nestIntoXmlMode()
          getXmlToken()
        case _ =>
          accept()
          setContentType(DEFAULT_CONTENT_TYPE)
          getOrdinaryScala()
      }
      case '"' =>
        val multiline = ch(1) == '"' && ch(2) == '"'
        val isInterpolation = Character.isUnicodeIdentifierPart(ch(-1)) // TODO: More precise detection
        if (isInterpolation)
          nestIntoStringInterpolationMode(multiline)
        if (multiline) {
          setContentType(SCALA_MULTI_LINE_STRING)
          accept(3)
          getMultiLineStringLit(quotesRequired = 3, isInterpolation)
        } else {
          setContentType(JAVA_STRING)
          accept()
          getStringLit(isInterpolation)
        }
      case '/' =>
        (ch(1): @switch) match {
          case '/' =>
            accept(2)
            setContentType(JAVA_SINGLE_LINE_COMMENT)
            getSingleLineComment()
          case '*' =>
            accept(2)
            if (ch == '*' && ch(1) != '/') {
              accept()
              setContentType(JAVA_DOC)
            } else
              setContentType(JAVA_MULTI_LINE_COMMENT)
            getMultiLineComment(nesting = 1)
          case _ =>
            accept()
            setContentType(DEFAULT_CONTENT_TYPE)
            getOrdinaryScala()
        }
      case '\'' => scanForCharLit() match {
        case Some(offset) =>
          accept(offset + 1)
          setContentType(JAVA_CHARACTER)
        case None =>
          accept()
          setContentType(DEFAULT_CONTENT_TYPE)
          getOrdinaryScala()
      }
      case '`' =>
        accept()
        setContentType(DEFAULT_CONTENT_TYPE)
        getBackQuotedIdent()
      case _ =>
        setContentType(DEFAULT_CONTENT_TYPE)
        getOrdinaryScala()
    }
  }

  private def scanForCharLit(): Option[Int] =
    if (ch(1) == '\\')
      if (ch(2) == 'u')
        if (ch(3) == '\'') Some(3)
        else if (ch(4) == '\'') Some(4)
        else if (ch(5) == '\'') Some(5)
        else if (ch(6) == '\'') Some(6)
        else if (ch(7) == '\'') Some(7)
        else None
      else if (ch(2) == '0')
        if (ch(3) == '\'') Some(3)
        else if (ch(4) == '\'') Some(4)
        else if (ch(5) == '\'') Some(5)
        else None
      else if (ch(3) == '\'')
        Some(3)
      else
        None
    else if (ch(2) == '\'')
      Some(2)
    else
      None

  @tailrec
  private def getStringLit(isInterpolation: Boolean): Unit =
    (ch: @switch) match {
      case '"' =>
        accept()
        if (isInterpolation)
          modeStack.pop()
      case EOF =>
        if (isInterpolation)
          modeStack.pop()
      case '\n' =>
        accept()
        if (isInterpolation)
          modeStack.pop()
      case '\r' if ch(1) != '\n' =>
        if (isInterpolation)
          modeStack.pop()
      case '\\' if ch(1) == '"' || ch(1) == '\\' =>
        accept(2)
        getStringLit(isInterpolation)
      case '$' if ch(1) == '$' =>
        accept(2)
        getStringLit(isInterpolation)
      case '$' if isInterpolation && ch(1) == '{' =>
        accept()
        nestIntoScalaMode()
      case '$' if isInterpolation =>
        accept()
        stringInterpolationState.embeddedIdentifierNext = true
      case _ =>
        accept()
        getStringLit(isInterpolation)
    }

  @tailrec
  private def getBackQuotedIdent(): Unit =
    (ch: @switch) match {
      case '`' => accept()
      case EOF =>
      case '\n' => accept()
      case '\r' if ch(1) != '\n' =>
      case _ =>
        accept()
        getBackQuotedIdent()
    }

  @tailrec
  private def getMultiLineStringLit(quotesRequired: Int, isInterpolation: Boolean): Unit =
    (ch: @switch) match {
      case '"' =>
        accept()
        getMultiLineStringLit(quotesRequired - 1, isInterpolation)
      case EOF =>
        if (isInterpolation)
          modeStack.pop()
      case '$' if ch(1) == '$' =>
        accept(2)
        getMultiLineStringLit(quotesRequired, isInterpolation)
      case '$' if isInterpolation && ch(1) == '{' =>
        accept()
        nestIntoScalaMode()
      case '$' if isInterpolation =>
        accept()
        stringInterpolationState.embeddedIdentifierNext = true
      case _ =>
        if (quotesRequired > 0) {
          accept()
          getMultiLineStringLit(3, isInterpolation)
        } else if (isInterpolation)
          modeStack.pop()
    }

  @tailrec
  private def getOrdinaryScala(): Unit =
    (ch: @switch) match {
      case EOF | '"' | '`' =>
      case '\'' if scanForCharLit().isDefined =>
      case '/' =>
        (ch(1): @switch) match {
          case '/' | '*' =>
          case _ =>
            accept()
            getOrdinaryScala()
        }
      case '<' => ch(-1) match {
        case EOF | ' ' | '\t' | '\n' | '{' | '(' | '>' if (isNameStart(ch(1)) || ch(1) == '!' || ch(1) == '?') =>
        case _ =>
          accept()
          getOrdinaryScala()
      }
      case '{' =>
        scalaState.nesting += 1
        accept()
        getOrdinaryScala()
      case '}' =>
        scalaState.nesting -= 1
        accept()
        if (scalaState.nesting == 0 && modeStack.size > 1)
          modeStack.pop()
        else
          getOrdinaryScala()
      case _ =>
        accept()
        getOrdinaryScala()
    }

  @tailrec
  private def getMultiLineComment(nesting: Int): Unit = {
    (ch: @switch) match {
      case '*' if (ch(1) == '/') =>
        accept(2)
        if (nesting > 1)
          getMultiLineComment(nesting - 1)
      case '/' if (ch(1) == '*') =>
        accept(2)
        getMultiLineComment(nesting + 1)
      case '{' if ch(1) == '{' && ch(2) == '{' =>
        nestIntoScaladocCodeBlockMode(nesting)
      case EOF =>
      case _ =>
        accept()
        getMultiLineComment(nesting)
    }
  }

  @tailrec
  private def getCodeBlockComment(nesting: Int): Unit =
    (ch: @switch) match {
      case '*' if ch(1) == '/' =>
        accept(2)
        if (nesting > 1)
          getCodeBlockComment(nesting - 1)
        else
          setContentType(JAVA_DOC)
      case '/' if ch(1) == '*' =>
        accept(2)
        getCodeBlockComment(nesting + 1)
      case '}' if ch(1) == '}' && ch(2) == '}' =>
        nestIntoScaladocMode(nesting)
        accept(3)
      case EOF =>
      case _ =>
        accept()
        getCodeBlockComment(nesting)
    }

  @tailrec
  private def getSingleLineComment(): Unit =
    (ch: @switch) match {
      case EOF =>
      case '\n' =>
        accept()
      case '\r' if ch(1) != '\n' =>
        accept()
      case _ =>
        accept()
        getSingleLineComment()
    }

  private def getStringInterpolationToken(multiline: Boolean, embeddedIdentifierNext: Boolean) {
    if (embeddedIdentifierNext) {
      setContentType(DEFAULT_CONTENT_TYPE)
      stringInterpolationState.embeddedIdentifierNext = false
      do
        accept()
      while (ch != EOF && Character.isUnicodeIdentifierPart(ch))
    } else {
      if (multiline) {
        setContentType(SCALA_MULTI_LINE_STRING)
        getMultiLineStringLit(quotesRequired = 3, isInterpolation = true)
      } else {
        setContentType(JAVA_STRING)
        getStringLit(isInterpolation = true)
      }
    }
  }

  private sealed trait ScannerMode
  private case class XmlState(var nesting: Int, var inTag: Option[Boolean]) extends ScannerMode
  private case class ScalaState(var nesting: Int) extends ScannerMode
  private case class StringInterpolationState(multiline: Boolean, var embeddedIdentifierNext: Boolean) extends ScannerMode
  private case class ScaladocCodeBlockState(val nesting: Int) extends ScannerMode
  private case class ScaladocState(val nesting: Int) extends ScannerMode

  private val modeStack: Stack[ScannerMode] = {
    val stack = new Stack[ScannerMode]
    stack.push(new ScalaState(nesting = 0))
    stack
  }

  private def xmlState = modeStack.head.asInstanceOf[XmlState]
  private def scalaState = modeStack.head.asInstanceOf[ScalaState]
  private def stringInterpolationState = modeStack.head.asInstanceOf[StringInterpolationState]

  private def nestIntoScalaMode() {
    modeStack.push(ScalaState(nesting = 0))
  }

  private def nestIntoXmlMode() {
    modeStack.push(XmlState(nesting = 0, inTag = None))
  }

  private def nestIntoStringInterpolationMode(multiline: Boolean) {
    modeStack.push(StringInterpolationState(multiline, embeddedIdentifierNext = false))
  }

  private def nestIntoScaladocCodeBlockMode(nesting: Int) {
    modeStack.push(ScaladocCodeBlockState(nesting))
  }

  private def nestIntoScaladocMode(nesting: Int) {
    modeStack.push(ScaladocState(nesting))
  }

  private def getXmlToken(): Unit =
    if (xmlState.inTag.isDefined) {
      val isEndTag = xmlState.inTag.get
      xmlState.inTag = None
      setContentType(XML_TAG)
      val (nestingAlteration, embeddedScalaInterrupt) = getXmlTag(isEndTag)
      if (embeddedScalaInterrupt) {
        xmlState.inTag = Some(isEndTag)
        nestIntoScalaMode()
      } else {
        xmlState.nesting += nestingAlteration
        if (xmlState.nesting == 0)
          modeStack.pop()
      }
    } else
      (ch: @switch) match {
        case '<' =>
          if (ch(1) == '!') {
            if (ch(2) == '-' && ch(3) == '-') {
              accept(4)
              setContentType(XML_COMMENT)
              getXmlComment()
              if (xmlState.nesting == 0)
                modeStack.pop()
            } else if (ch(2) == '[' && ch(3) == 'C' && ch(4) == 'D' && ch(5) == 'A' && ch(6) == 'T' && ch(7) == 'A' && ch(8) == '[') {
              accept(9)
              setContentType(XML_CDATA)
              getXmlCDATA()
              if (xmlState.nesting == 0)
                modeStack.pop()
            } else {
              accept(2)
              setContentType(XML_PCDATA)
              getXmlCharData()
            }
          } else if (ch(1) == '?') {
            accept(2)
            setContentType(XML_PI)
            getXmlProcessingInstruction()
            if (xmlState.nesting == 0)
              modeStack.pop()
            // } else if (... TODO: <xml:unparsed>) {}
          } else {
            setContentType(XML_TAG)
            val isEndTag = ch(1) == '/'
            accept()
            val (nestingAlteration, embeddedScalaInterrupt) = getXmlTag(isEndTag)
            if (embeddedScalaInterrupt) {
              xmlState.inTag = Some(isEndTag)
              nestIntoScalaMode()
            } else {
              xmlState.nesting += nestingAlteration
              if (xmlState.nesting == 0)
                modeStack.pop()
            }
          }
        case '{' if ch(1) != '{' =>
          nestIntoScalaMode()
          getScalaToken()
        case '{' if ch(1) == '{' =>
          setContentType(XML_PCDATA)
          accept(2)
          getXmlCharData()
        case _ =>
          setContentType(XML_PCDATA)
          getXmlCharData()
      }

  @tailrec
  private def getXmlCharData(): Unit =
    (ch: @switch) match {
      case EOF | '<' =>
      case '{' if ch(1) == '{' =>
        accept(2)
        getXmlCharData()
      case '{' if ch(1) != '{' =>
        nestIntoScalaMode()
      case _ =>
        accept()
        getXmlCharData()
    }

  /**
   * Read an Xml tag, or part of one up to a Scala escape.
   * @return nesting alteration (0, 1 or -1) showing the change to the depth of XML tag nesting,
   * and whether the tag scanning was interrupted by embedded Scala.
   */
  @tailrec
  private def getXmlTag(isEndTag: Boolean): (Int, Boolean) =
    (ch: @switch) match {
      case EOF => (0, false)
      case '"' =>
        accept()
        getXmlAttributeValue('"')
        getXmlTag(isEndTag)
      case '\'' =>
        accept()
        getXmlAttributeValue('\'')
        getXmlTag(isEndTag)
      case '{' if ch(1) == '{' =>
        accept(2)
        (0, false)
      case '{' if ch(1) != '{' =>
        (0, true)
      case '/' if ch(1) == '>' && !isEndTag => // an empty tag
        accept(2)
        (0, false)
      case '>' =>
        if (isEndTag) {
          accept()
          (-1, false)
        } else {
          accept()
          (1, false)
        }
      case _ =>
        accept()
        getXmlTag(isEndTag)
    }

  @tailrec
  private def getXmlAttributeValue(quote: Char): Unit =
    ch match {
      case EOF =>
      case `quote` =>
        accept()
      case _ =>
        accept()
        getXmlAttributeValue(quote)
    }

  @tailrec
  private def getXmlProcessingInstruction(): Unit =
    (ch: @switch) match {
      case EOF =>
      case '?' if ch(1) == '>' =>
        accept(2)
      case _ =>
        accept()
        getXmlProcessingInstruction()
    }

  @tailrec
  private def getXmlCDATA(): Unit =
    (ch: @switch) match {
      case EOF =>
      case ']' if ch(1) == ']' && ch(2) == '>' =>
        accept(3)
      case _ =>
        accept()
        getXmlCDATA()
    }

  @tailrec
  private def getXmlComment(): Unit =
    (ch: @switch) match {
      case EOF =>
      case '-' if ch(1) == '-' && ch(2) == '>' =>
        accept(3)
      case _ =>
        accept()
        getXmlComment()
    }

}
