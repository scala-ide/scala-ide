package org.scalaide.ui.internal.editor

import org.eclipse.core.runtime.Assert
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.ToolFactory
import org.eclipse.jdt.core.compiler.IProblem
import org.eclipse.jdt.core.compiler.ITerminalSymbols
import org.eclipse.jdt.core.compiler.InvalidInputException
import org.eclipse.jdt.core.dom.AST
import org.eclipse.jdt.core.dom.ASTNode
import org.eclipse.jdt.core.dom.ASTParser
import org.eclipse.jdt.core.dom.CompilationUnit
import org.eclipse.jdt.core.dom.DoStatement
import org.eclipse.jdt.core.dom.ForStatement
import org.eclipse.jdt.core.dom.IfStatement
import org.eclipse.jdt.core.dom.WhileStatement
import org.eclipse.jdt.internal.corext.dom.NodeFinder
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.text.JavaHeuristicScanner
import org.eclipse.jdt.internal.ui.text.Symbols
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jdt.ui.text.IJavaPartitions
import org.eclipse.jface.text.BadLocationException
import org.eclipse.jface.text.DefaultIndentLineAutoEditStrategy
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jface.text.Region
import org.eclipse.jface.text.TextUtilities
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.ui.texteditor.ITextEditorExtension3

/**
 * Auto indent strategy sensitive to brackets.
 *
 * @param partitioning the document partitioning
 * @param project the project to get formatting preferences from, or null to use default preferences
 * @param viewer the source viewer that this strategy is attached to
 */
class ScalaAutoIndentStrategy(
    val fPartitioning : String,
    val fProject : IJavaProject,
    val fViewer : ISourceViewer,
    val preferencesProvider : PreferenceProvider
  ) extends DefaultIndentLineAutoEditStrategy {

  private class CompilationUnitInfo(
    var buffer : Array[Char],
    var delta : Int
  )

  private def fCloseBrace = preferencesProvider.getBoolean(PreferenceConstants.EDITOR_CLOSE_BRACES)
  private var fIsSmartMode = false
  private def fIsSmartTab = preferencesProvider.getBoolean(PreferenceConstants.EDITOR_SMART_TAB)

  // TODO This could be in a singleton as in the original, but that sucks.
  private val fgScanner = ToolFactory.createScanner(false, false, false, false)

  /**
   * Determine the count of brackets within a given area of the document
   */
  private def getBracketCount(d : IDocument, startOffset : Int, endOffset : Int, pIgnoreCloseBrackets : Boolean) : Int = {
    var bracketCount = 0
    var ignoreCloseBrackets = pIgnoreCloseBrackets
    var offset = startOffset

    while (offset < endOffset) {
      val curr = d.getChar(offset)
      offset += 1
      curr match {
        case '/' =>
          if (offset < endOffset) {
            val next = d.getChar(offset)
            if (next == '*') {
              // a comment starts, advance to the comment end
              offset = getCommentEnd(d, offset + 1, endOffset)
            } else if (next == '/') {
              // '//'-comment: nothing to do anymore on this line
              offset = endOffset
            }
          }
        case '*' =>
          if (offset < endOffset) {
            val next = d.getChar(offset)
            if (next == '/') {
              // we have been in a comment: forget what we read before
              bracketCount = 0
              offset += 1
            }
          }
        case '{' =>
          bracketCount += 1
          ignoreCloseBrackets = false
        case '}' =>
          if (!ignoreCloseBrackets) {
            bracketCount -= 1
          }
        case '"' | '\'' =>
          offset = getStringEnd(d, offset, endOffset, curr)
        case _ =>
      }
    }
    return bracketCount
  }

  /**
   * Find the end of a comment
   */
  private def getCommentEnd(d : IDocument, initialOffset : Int, endOffset : Int) : Int = {
    var offset = initialOffset
    while (offset < endOffset) {
      val curr = d.getChar(offset)
      offset += 1
      if (curr == '*' && offset < endOffset && d.getChar(offset) == '/') {
          return offset + 1
      }
    }
    return endOffset
  }

  private def getIndentOfLine(d : IDocument, line : Int) : String = {
    if (line > -1) {
      val start = d.getLineOffset(line)
      val end = start + d.getLineLength(line) - 1
      val whiteEnd = findEndOfWhiteSpace(d, start, end)
      return d.get(start, whiteEnd - start)
    } else {
      return ""
    }
  }

  private def getStringEnd(d : IDocument, initialOffset : Int, endOffset : Int, ch : Char) : Int = {
    var offset = initialOffset
    while (offset < endOffset) {
      val curr = d.getChar(offset)
      offset += 1
      if (curr == '\\') {
        // ignore escaped characters
        offset += 1
      } else if (curr == ch) {
        return offset
      }
    }
    return endOffset
  }

  private def createIndenter(d : IDocument, scanner : JavaHeuristicScanner) : ScalaIndenter = {
    return new ScalaIndenter(d, scanner, fProject, preferencesProvider)
  }

  private def smartIndentAfterClosingBracket(d : IDocument, c : DocumentCommand) : Unit = {
    if (c.offset == -1 || d.getLength() == 0)
      return

    try {
      val p = if (c.offset == d.getLength()) c.offset - 1 else c.offset
      val line = d.getLineOfOffset(p)
      val start = d.getLineOffset(line)
      val whiteend = findEndOfWhiteSpace(d, start, c.offset)

      val scanner = new JavaHeuristicScanner(d)
      val indenter = createIndenter(d, scanner)

      // shift only when line does not contain any text up to the closing bracket
      if (whiteend == c.offset) {
        // evaluate the line with the opening bracket that matches out closing bracket
        val reference = indenter.findReferencePosition(c.offset, false, true, false, false, false)
        val indLine = d.getLineOfOffset(reference)
        if (indLine != -1 && indLine != line) {
          // take the indent of the found line
          val replaceText = new StringBuffer(getIndentOfLine(d, indLine))
          // add the rest of the current line including the just added close bracket
          replaceText.append(d.get(whiteend, c.offset - whiteend))
          replaceText.append(c.text)
          // modify document command
          c.length += c.offset - start
          c.offset = start
          c.text= replaceText.toString()
        }
      }
    } catch {
      case e : BadLocationException =>
        JavaPlugin.log(e)
    }
  }

  private def smartIndentAfterOpeningBracket(d : IDocument, c : DocumentCommand) : Unit = {
    if (c.offset < 1 || d.getLength() == 0)
      return

    val scanner = new JavaHeuristicScanner(d)

    val p = if (c.offset == d.getLength()) c.offset - 1 else c.offset

    try {
      // current line
      val line = d.getLineOfOffset(p)
      val lineOffset = d.getLineOffset(line)

      // make sure we don't have any leading comments etc.
      if (d.get(lineOffset, p - lineOffset).trim().length() != 0)
        return

      // line of last Java code
      val pos= scanner.findNonWhitespaceBackward(p, JavaHeuristicScanner.UNBOUND)
      if (pos == -1)
        return
      val lastLine= d.getLineOfOffset(pos)

      // only shift if the last java line is further up and is a braceless block candidate
      if (lastLine < line) {

        val indenter = createIndenter(d, scanner)
        val indent = indenter.computeIndentation(p, true)
        val toDelete = d.get(lineOffset, c.offset - lineOffset)
        if (indent != null && !indent.toString().equals(toDelete)) {
          c.text = indent.append(c.text).toString()
          c.length += c.offset - lineOffset
          c.offset = lineOffset
        }
      }

    } catch {
      case e : BadLocationException =>
        JavaPlugin.log(e)
    }
  }

  private def smartIndentAfterNewLine(d : IDocument, c : DocumentCommand) : Unit = {
    val scanner = new JavaHeuristicScanner(d)
    val indenter = createIndenter(d, scanner)
    var indent = indenter.computeIndentation(c.offset)
    if (indent == null)
      indent = new StringBuffer()

    val docLength = d.getLength()
    if (c.offset == -1 || docLength == 0)
      return

    try {
      val p = if (c.offset == docLength) c.offset - 1 else c.offset
      val line= d.getLineOfOffset(p)

      val buf= new StringBuffer(c.text + indent)

      val reg = d.getLineInformation(line)
      val lineEnd = reg.getOffset() + reg.getLength()

      val contentStart = findEndOfWhiteSpace(d, c.offset, lineEnd)
      c.length = Math.max(contentStart - c.offset, 0)

      var start = reg.getOffset()
      val region = TextUtilities.getPartition(d, fPartitioning, start, true)
      if (IJavaPartitions.JAVA_DOC.equals(region.getType()))
        start = d.getLineInformationOfOffset(region.getOffset()).getOffset()

      // insert closing brace on new line after an unclosed opening brace
      if (getBracketCount(d, start, c.offset, true) > 0 && closeBrace && !isClosed(d, c.offset, c.length)) {
        c.caretOffset = c.offset + buf.length()
        c.shiftsCaret = false

        // copy old content of line behind insertion point to new line
        // unless we think we are inserting an anonymous type definition

        if ((c.offset == 0 || computeAnonymousPosition(d, c.offset - 1, fPartitioning, lineEnd) == -1)
            && lineEnd - contentStart > 0) {
            c.length =  lineEnd - c.offset
            buf.append(d.get(contentStart, lineEnd - contentStart).toCharArray())
        }

        buf.append(TextUtilities.getDefaultLineDelimiter(d))
        val nonWS = findEndOfWhiteSpace(d, start, lineEnd)
        val reference =
          if (nonWS < c.offset && d.getChar(nonWS) == '{')
            new StringBuffer(d.get(start, nonWS - start))
          else
            indenter.getReferenceIndentation(c.offset)
        if (reference != null)
          buf.append(reference)
        buf.append('}')
      }
      // insert extra line upon new line between two braces
      else if (c.offset > start && contentStart < lineEnd && d.getChar(contentStart) == '}') {
        val firstCharPos = scanner.findNonWhitespaceBackward(c.offset - 1, start)
        if (firstCharPos != JavaHeuristicScanner.NOT_FOUND && d.getChar(firstCharPos) == '{') {
          c.caretOffset = c.offset + buf.length()
          c.shiftsCaret = false

          val nonWS = findEndOfWhiteSpace(d, start, lineEnd)
          val reference =
            if (nonWS < c.offset && d.getChar(nonWS) == '{')
              new StringBuffer(d.get(start, nonWS - start))
            else
              indenter.getReferenceIndentation(c.offset)

          buf.append(TextUtilities.getDefaultLineDelimiter(d))

          if (reference != null)
            buf.append(reference)
        }
      }
      c.text = buf.toString()

    } catch {
      case e : BadLocationException =>
        JavaPlugin.log(e)
    }
  }

  /**
   * Computes an insert position for an opening brace if <code>offset</code> maps to a position in
   * <code>document</code> with a expression in parenthesis that will take a block after the closing parenthesis.
   *
   * @param document the document being modified
   * @param offset the offset of the caret position, relative to the line start.
   * @param partitioning the document partitioning
   * @param max the max position
   * @return an insert position relative to the line start if <code>line</code> contains a parenthesized expression that can be followed by a block, -1 otherwise
   */
  private def computeAnonymousPosition(document : IDocument, offset : Int, partitioning : String, max : Int) : Int = {
    // find the opening parenthesis for every closing parenthesis on the current line after offset
    // return the position behind the closing parenthesis if it looks like a method declaration
    // or an expression for an if, while, for, catch statement

    val scanner = new JavaHeuristicScanner(document)
    val pos = offset
    val length = max
    var scanTo = scanner.scanForward(pos, length, '}')
    if (scanTo == -1)
      scanTo = length

    var closingParen = findClosingParenToLeft(scanner, pos) - 1
    var openingParen = -1
    val hasNewToken = looksLikeAnonymousClassDef(document, partitioning, scanner, pos)
    var done = false
    while (!done) {
      val startScan= closingParen + 1
      closingParen = scanner.scanForward(startScan, scanTo, ')')
      if (closingParen == -1) {
        if (hasNewToken && openingParen != -1)
          return openingParen + 1
        done = true
      } else {

        openingParen = scanner.findOpeningPeer(closingParen - 1, '(', ')');

        // no way an expression at the beginning of the document can mean anything
        if (openingParen < 1) {
          done = true
        } else {
          // only select insert positions for parenthesis currently embracing the caret
          if (openingParen <= pos && looksLikeAnonymousClassDef(document, partitioning, scanner, openingParen - 1))
              return closingParen + 1;
        }
      }
    }
    return -1
  }

  /**
   * Finds a closing parenthesis to the left of <code>position</code> in document, where that parenthesis is only
   * separated by whitespace from <code>position</code>. If no such parenthesis can be found, <code>position</code> is returned.
   *
   * @param scanner the java heuristic scanner set up on the document
   * @param position the first character position in <code>document</code> to be considered
   * @return the position of a closing parenthesis left to <code>position</code> separated only by whitespace, or <code>position</code> if no parenthesis can be found
   */
  private def findClosingParenToLeft(scanner : JavaHeuristicScanner, position : Int) : Int = {
    if (position < 1)
      return position

    if (scanner.previousToken(position - 1, JavaHeuristicScanner.UNBOUND) == Symbols.TokenRPAREN)
      return scanner.getPosition() + 1

    return position
  }

  /**
   * Checks whether the content of <code>document</code> in the range (<code>offset</code>, <code>length</code>)
   * contains the <code>new</code> keyword.
   *
   * @param document the document being modified
   * @param offset the first character position in <code>document</code> to be considered
   * @param length the length of the character range to be considered
   * @param partitioning the document partitioning
   * @return <code>true</code> if the specified character range contains a <code>new</code> keyword, <code>false</code> otherwise.
   */
  private def isNewMatch(document : IDocument, offset : Int, length : Int, partitioning : String) : Boolean = {
    Assert.isTrue(length >= 0)
    Assert.isTrue(offset >= 0)
    Assert.isTrue(offset + length < document.getLength() + 1)

    try {
      val text = document.get(offset, length)
      var pos = text.indexOf("new")

      while (pos != -1 && !isDefaultPartition(document, pos + offset, partitioning))
        pos = text.indexOf("new", pos + 2)

      if (pos < 0)
        return false

      if (pos != 0 && Character.isJavaIdentifierPart(text.charAt(pos - 1)))
        return false

      if (pos + 3 < length && Character.isJavaIdentifierPart(text.charAt(pos + 3)))
        return false

      return true

    } catch {
      case _ : BadLocationException => // Ignore this exception
    }
    return false
  }

  /**
   * Checks whether the content of <code>document</code> at <code>position</code> looks like an
   * anonymous class definition. <code>position</code> must be to the left of the opening
   * parenthesis of the definition's parameter list.
   *
   * @param document the document being modified
   * @param partitioning the document partitioning
   * @param scanner the scanner
   * @param position the first character position in <code>document</code> to be considered
   * @return <code>true</code> if the content of <code>document</code> looks like an anonymous class definition, <code>false</code> otherwise
   */
  private def looksLikeAnonymousClassDef(document : IDocument, partitioning : String, scanner : JavaHeuristicScanner, position : Int) : Boolean = {
    val previousCommaParenEqual = scanner.scanBackward(position - 1, JavaHeuristicScanner.UNBOUND, Array[Char](',', '(', '='))
    if (previousCommaParenEqual == -1 || position < previousCommaParenEqual + 5) // 2 for borders, 3 for "new"
      return false

    if (isNewMatch(document, previousCommaParenEqual + 1, position - previousCommaParenEqual - 2, partitioning))
      return true

    return false
  }

  /**
   * Checks whether <code>position</code> resides in a default (Java) partition of <code>document</code>.
   *
   * @param document the document being modified
   * @param position the position to be checked
   * @param partitioning the document partitioning
   * @return <code>true</code> if <code>position</code> is in the default partition of <code>document</code>, <code>false</code> otherwise
   */
  private def isDefaultPartition(document : IDocument, position : Int, partitioning : String) : Boolean = {
    Assert.isTrue(position >= 0)
    Assert.isTrue(position <= document.getLength())

    try {
      val region = TextUtilities.getPartition(document, partitioning, position, false)
      return region.getType().equals(IDocument.DEFAULT_CONTENT_TYPE)
    } catch {
      case _ : BadLocationException => // Ignore this exception
    }

    return false
  }

  private def isClosed(document : IDocument, offset : Int, length : Int) : Boolean = {

    val info = getCompilationUnitForMethod(document, offset)
    if (info == null)
      return false

    var compilationUnit : CompilationUnit = null
    try {
      val parser = ASTParser.newParser(AST.JLS10)
      parser.setSource(info.buffer)
      compilationUnit = parser.createAST(null).asInstanceOf[CompilationUnit]
    } catch {
      case _ : ArrayIndexOutOfBoundsException =>
        // work around for parser problem
        return false
    }

    val problems = compilationUnit.getProblems()
    for (i <- 0 until problems.length) {
      if (problems(i).getID() == IProblem.UnmatchedBracket)
        return true
    }

    val relativeOffset = offset - info.delta

    var node = NodeFinder.perform(compilationUnit, relativeOffset, length)

    if (length == 0) {
      while (node != null && (relativeOffset == node.getStartPosition() || relativeOffset == node.getStartPosition() + node.getLength()))
        node = node.getParent()
    }

    if (node == null)
      return false

    node.getNodeType() match {
      case ASTNode.BLOCK =>
        return getBlockBalance(document, offset, fPartitioning) <= 0

      case ASTNode.IF_STATEMENT =>
        val ifStatement = node.asInstanceOf[IfStatement]
        val expression = ifStatement.getExpression()
        val expressionRegion = createRegion(expression, info.delta)
        val thenStatement = ifStatement.getThenStatement()
        val thenRegion = createRegion(thenStatement, info.delta)

        // between expression and then statement
        if (expressionRegion.getOffset() + expressionRegion.getLength() <= offset && offset + length <= thenRegion.getOffset())
          return thenStatement != null

        val elseStatement = ifStatement.getElseStatement()
        val elseRegion = createRegion(elseStatement, info.delta)

        if (elseStatement != null) {
          val sourceOffset = thenRegion.getOffset() + thenRegion.getLength()
          val sourceLength = elseRegion.getOffset() - sourceOffset
          val elseToken = getToken(document, new Region(sourceOffset, sourceLength), ITerminalSymbols.TokenNameelse)
          return elseToken != null && elseToken.getOffset() + elseToken.getLength() <= offset && offset + length < elseRegion.getOffset()
        }

      case ASTNode.WHILE_STATEMENT =>
        val expression = node.asInstanceOf[WhileStatement].getExpression()
        val expressionRegion = createRegion(expression, info.delta)
        val body = node.asInstanceOf[WhileStatement].getBody()
        val bodyRegion = createRegion(body, info.delta)

        // between expression and body statement
        if (expressionRegion.getOffset() + expressionRegion.getLength() <= offset && offset + length <= bodyRegion.getOffset())
          return body != null

      case ASTNode.FOR_STATEMENT =>
        val expression = node.asInstanceOf[ForStatement].getExpression()
        val expressionRegion = createRegion(expression, info.delta)
        val body = node.asInstanceOf[ForStatement].getBody()
        val bodyRegion = createRegion(body, info.delta)

        // between expression and body statement
        if (expressionRegion.getOffset() + expressionRegion.getLength() <= offset && offset + length <= bodyRegion.getOffset())
          return body != null

      case ASTNode.DO_STATEMENT =>
        val doStatement = node.asInstanceOf[DoStatement]
        val doRegion = createRegion(doStatement, info.delta)
        val body = doStatement.getBody()
        val bodyRegion = createRegion(body, info.delta)

        if (doRegion.getOffset() + doRegion.getLength() <= offset && offset + length <= bodyRegion.getOffset())
          return body != null

      case _ =>
        // Do nothing
    }

    return true
  }

  /**
   * The preference setting for the visual tabulator display.
   *
   * @return the number of spaces displayed for a tabulator in the editor
   */
  private def getVisualTabLengthPreference: Int =
    preferencesProvider.getInt(ScalaIndenter.TAB_SIZE)

  /**
   * The preference setting that tells whether to insert spaces when pressing the Tab key.
   *
   * @return <code>true</code> if spaces are inserted when pressing the Tab key
   * @since 3.5
   */
  private def isInsertingSpacesForTab: Boolean =
    preferencesProvider.getBoolean(ScalaIndenter.INDENT_WITH_TABS)

  private def isLineDelimiter(document : IDocument, text : String) : Boolean = {
    val delimiters = document.getLegalLineDelimiters()
    if (delimiters != null)
      return TextUtilities.equals(delimiters, text) > -1
    return false
  }

  private def smartIndentOnKeypress(document : IDocument, command : DocumentCommand) : Unit = {
    command.text.charAt(0) match {
      case '}' =>
        smartIndentAfterClosingBracket(document, command)
      case '{' =>
        smartIndentAfterOpeningBracket(document, command);
      case 'e' =>
        smartIndentUponE(document, command)
      case _ =>
        // Do nothing
    }
  }

  private def smartIndentUponE(d : IDocument, c : DocumentCommand) : Unit = {
    if (c.offset < 4 || d.getLength() == 0)
      return

    try {
      val content = d.get(c.offset - 3, 3)
      if (content.equals("els")) {
        val scanner= new JavaHeuristicScanner(d)
        val p= c.offset - 3

        // current line
        val line= d.getLineOfOffset(p);
        val lineOffset= d.getLineOffset(line)

        // make sure we don't have any leading comments etc.
        if (d.get(lineOffset, p - lineOffset).trim().length() != 0)
          return

        // line of last Java code
        val pos = scanner.findNonWhitespaceBackward(p - 1, JavaHeuristicScanner.UNBOUND)
        if (pos == -1)
          return
        val lastLine= d.getLineOfOffset(pos)

        // only shift if the last java line is further up and is a braceless block candidate
        if (lastLine < line) {

          val indenter = createIndenter(d, scanner)
          val ref = indenter.findReferencePosition(p, true, false, false, false, false)
          if (ref == JavaHeuristicScanner.NOT_FOUND)
            return
          val refLine = d.getLineOfOffset(ref)
          val indent = getIndentOfLine(d, refLine)

          if (indent != null) {
            c.text = indent + "else"
            c.length += c.offset - lineOffset
            c.offset = lineOffset
          }
        }

        return
      }

      if (content.equals("cas")) {
        val scanner = new JavaHeuristicScanner(d)
        val p = c.offset - 3

        // current line
        val line = d.getLineOfOffset(p)
        val lineOffset = d.getLineOffset(line)

        // make sure we don't have any leading comments etc.
        if (d.get(lineOffset, p - lineOffset).trim().length() != 0)
          return

        // line of last Java code
        val pos = scanner.findNonWhitespaceBackward(p - 1, JavaHeuristicScanner.UNBOUND)
        if (pos == -1)
          return
        val lastLine= d.getLineOfOffset(pos)

        // only shift if the last java line is further up and is a braceless block candidate
        if (lastLine < line) {

          val indenter = createIndenter(d, scanner)
          val ref = indenter.findReferencePosition(p, false, false, false, true, false)
          if (ref == JavaHeuristicScanner.NOT_FOUND)
            return
          val refLine= d.getLineOfOffset(ref)
          val nextToken= scanner.nextToken(ref, JavaHeuristicScanner.UNBOUND)
          val indent =
            if (nextToken == Symbols.TokenCASE || nextToken == Symbols.TokenDEFAULT)
              getIndentOfLine(d, refLine)
            else // at the brace of the switch
              indenter.computeIndentation(p).toString()

          if (indent != null) {
            c.text = indent + "case"
            c.length += c.offset - lineOffset
            c.offset = lineOffset
          }
        }

        return
      }

    } catch {
      case e : BadLocationException =>
        JavaPlugin.log(e)
    }
  }

  /*
   * @see org.eclipse.jface.text.IAutoIndentStrategy#customizeDocumentCommand(org.eclipse.jface.text.IDocument, org.eclipse.jface.text.DocumentCommand)
   */
  override def customizeDocumentCommand(d : IDocument, c : DocumentCommand) : Unit = {

    if (c.doit == false)
      return

    clearCachedValues

    if (!fIsSmartMode) {
      super.customizeDocumentCommand(d, c)
      return
    }

    if (!fIsSmartTab && isRepresentingTab(c.text))
      return

    if (c.length == 0 && c.text != null && isLineDelimiter(d, c.text))
      smartIndentAfterNewLine(d, c)
    else if (c.text.length() == 1)
      smartIndentOnKeypress(d, c)
  }

  /**
   * Tells whether the given inserted string represents hitting the Tab key.
   *
   * @param text the text to check
   * @return <code>true</code> if the text represents hitting the Tab key
   * @since 3.5
   */
  private def isRepresentingTab(text : String) : Boolean = {
    if (text == null)
      return false

    if (isInsertingSpacesForTab) {
      if (text.length() == 0 || text.length() > getVisualTabLengthPreference)
        return false
      for (i <- 0 until text.length()) {
        if (text.charAt(i) != ' ')
          return false
      }
      return true
    } else
      return text.length() == 1 && text.charAt(0) == '\t'
  }

  private def closeBrace : Boolean = fCloseBrace

  private def clearCachedValues(): Unit = {
    preferencesProvider.updateCache
    fIsSmartMode = computeSmartMode
  }

  protected def computeSmartMode : Boolean = {
    val page = JavaPlugin.getActivePage()
    if (page != null) {
      val part = page.getActiveEditor()
      if (part.isInstanceOf[ITextEditorExtension3]) {
        val extension = part.asInstanceOf[ITextEditorExtension3]
        return extension.getInsertMode() == ITextEditorExtension3.SMART_INSERT
      }
    }
    return false
  }

  private def getCompilationUnitForMethod(document : IDocument, offset : Int) : CompilationUnitInfo = {
    try {
      val scanner = new JavaHeuristicScanner(document)

      val sourceRange = scanner.findSurroundingBlock(offset)
      if (sourceRange == null)
        return null
      val source = document.get(sourceRange.getOffset(), sourceRange.getLength())

      val contents = new StringBuffer()
      contents.append("class ____C{void ____m()")
      val methodOffset = contents.length()
      contents.append(source)
      contents.append('}')

      val buffer = contents.toString().toCharArray()

      return new CompilationUnitInfo(buffer, sourceRange.getOffset() - methodOffset)

    } catch {
      case e : BadLocationException =>
        JavaPlugin.log(e)
    }

    return null
  }

  /**
   * Returns the block balance, i.e. zero if the blocks are balanced at <code>offset</code>, a
   * negative number if there are more closing than opening braces, and a positive number if there
   * are more opening than closing braces.
   *
   * @param document the document
   * @param offset the offset
   * @param partitioning the partitioning
   * @return the block balance
   */
  private def getBlockBalance(document : IDocument, offset : Int, partitioning : String) : Int = {
    if (offset < 1)
      return -1
    if (offset >= document.getLength())
      return 1

    var begin = offset
    var end = offset - 1

    val scanner = new JavaHeuristicScanner(document)

    while (true) {
      begin = scanner.findOpeningPeer(begin - 1, '{', '}')
      end = scanner.findClosingPeer(end + 1, '{', '}')
      if (begin == -1 && end == -1)
        return 0
      if (begin == -1)
        return -1
      if (end == -1)
        return 1
    }

    return 0 // Just needed to keep the compiler happy
  }

  private def createRegion(node : ASTNode, delta : Int) : IRegion = {
    if (node == null)
      return null
    else
      return new Region(node.getStartPosition() + delta, node.getLength())
  }

  private def getToken(document : IDocument, scanRegion : IRegion, tokenId : Int) : IRegion = {

    try {
      val source = document.get(scanRegion.getOffset(), scanRegion.getLength())

      fgScanner.setSource(source.toCharArray())

      var id= fgScanner.getNextToken()
      while (id != ITerminalSymbols.TokenNameEOF && id != tokenId)
        id = fgScanner.getNextToken()

      if (id == ITerminalSymbols.TokenNameEOF)
        return null

      val tokenOffset= fgScanner.getCurrentTokenStartPosition()
      val tokenLength= fgScanner.getCurrentTokenEndPosition() + 1 - tokenOffset // inclusive end
      return new Region(tokenOffset + scanRegion.getOffset(), tokenLength)

    } catch {
      case _ : InvalidInputException =>
        return null
      case _ : BadLocationException =>
        return null
    }
  }
}
