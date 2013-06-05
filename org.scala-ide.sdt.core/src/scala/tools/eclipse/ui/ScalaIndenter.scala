/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse.ui

import scalariform.formatter.preferences._

import org.eclipse.core.runtime.Assert
import org.eclipse.jface.text.BadLocationException
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.text.IRegion
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.core.JavaCore
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants
import org.eclipse.jdt.internal.ui.JavaPlugin
import org.eclipse.jdt.internal.ui.text.DocumentCharacterIterator
import org.eclipse.jdt.internal.ui.text.JavaHeuristicScanner
import org.eclipse.jdt.internal.ui.text.Symbols
import org.eclipse.jdt.internal.corext.util.CodeFormatterUtil
import org.eclipse.jdt.ui.PreferenceConstants
import java.lang.Math.min
import scala.collection.mutable.Map
import scala.annotation.tailrec
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.formatter.FormatterPreferences
import scalariform.formatter.preferences.IndentSpaces

import scala.util.control.Exception

// TODO Move this out into a new file
trait PreferenceProvider {
  private val preferences = Map.empty[String, String]

  def updateCache: Unit

  def put(key: String, value: String) {
    preferences(key) = value
  }

  def get(key: String): String = {
    preferences(key)
  }

  def getBoolean(key: String): Boolean = {
    get(key).toBoolean
  }

  def getInt(key: String): Int = {
    get(key).toInt
  }
}

// TODO Move this out into a new file
class JdtPreferenceProvider(val project: IJavaProject) extends PreferenceProvider {
  private def preferenceStore = JavaPlugin.getDefault().getCombinedPreferenceStore()

  def updateCache: Unit = {
    put(PreferenceConstants.EDITOR_CLOSE_BRACES,
      preferenceStore.getBoolean(PreferenceConstants.EDITOR_CLOSE_BRACES).toString)
    put(PreferenceConstants.EDITOR_SMART_TAB,
      preferenceStore.getBoolean(PreferenceConstants.EDITOR_SMART_TAB).toString)

    val formatterPreferences = FormatterPreferences.getPreferences(project)
    val indentWithTabs = formatterPreferences(IndentWithTabs).toString
    val indentSpaces = formatterPreferences(IndentSpaces).toString

    put(ScalaIndenter.TAB_SIZE, indentSpaces)
    put(ScalaIndenter.INDENT_SIZE, indentSpaces)
    put(ScalaIndenter.INDENT_WITH_TABS, indentWithTabs)

    def populateFromProject(key: String) = {
      put(key, project.getOption(key, true))
    }

    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_EXPRESSIONS_IN_ARRAY_INITIALIZER)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_CONDITIONAL_EXPRESSION)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_INDENT_SWITCHSTATEMENTS_COMPARE_TO_SWITCH)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_INDENT_SWITCHSTATEMENTS_COMPARE_TO_CASES)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_PARAMETERS_IN_METHOD_DECLARATION)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_ARGUMENTS_IN_METHOD_INVOCATION)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_INDENT_STATEMENTS_COMPARE_TO_BLOCK)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_INDENT_STATEMENTS_COMPARE_TO_BODY)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_INDENT_BODY_DECLARATIONS_COMPARE_TO_TYPE_HEADER)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_ARRAY_INITIALIZER)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_METHOD_DECLARATION)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_TYPE_DECLARATION)
    populateFromProject(DefaultCodeFormatterConstants.FORMATTER_CONTINUATION_INDENTATION)
  }
}

/**
 * Holder for various constants used by the Scala indenter
 */
object ScalaIndenter {
  val TAB_SIZE = "tab-size"
  val INDENT_SIZE = "indent-size"
  val INDENT_WITH_TABS = "indent-with-tabs"
}

/**
 * Indenter for the Scala UI. Based largely on
 * {@link org.eclipse.jdt.internal.ui.text.JavaIndenter}.
 *
 * <p>
 * Uses the {@link org.eclipse.jdt.internal.ui.text.JavaHeuristicScanner} to
 * get the indentation level for a certain position in a document.
 * </p>
 *
 * <p>
 * An instance holds some internal position in the document and is therefore
 * not threadsafe.
 * </p>
 *
 * @since 3.0
 */
class ScalaIndenter(
  val document: IDocument,
  val scanner: JavaHeuristicScanner,
  val project: IJavaProject,
  val preferencesProvider: PreferenceProvider) {

  /**
   * Returns the possibly project-specific core preference defined under <code>key</code>.
   *
   * @param key the key of the preference
   * @return the value of the preference
   * @since 3.1
   */
  protected def getCoreFormatterOption(key: String): String = preferencesProvider.get(key)

  private def prefUseTabs = preferencesProvider.getBoolean(ScalaIndenter.INDENT_WITH_TABS)

  private def prefTabChar = if (prefUseTabs) JavaCore.TAB else JavaCore.SPACE

  private def prefTabSize = preferencesProvider.getInt(ScalaIndenter.TAB_SIZE)

  private def prefIndentationSize = preferencesProvider.getInt(ScalaIndenter.INDENT_SIZE)

  private def prefArrayDimensionsDeepIndent = true; // sensible default, no formatter setting

  private def prefArrayIndent: Int = {
    val option = getCoreFormatterOption(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_EXPRESSIONS_IN_ARRAY_INITIALIZER)
    try {
      if (DefaultCodeFormatterConstants.getIndentStyle(option) == DefaultCodeFormatterConstants.INDENT_BY_ONE)
        return 1
    } catch {
      case _: IllegalArgumentException => // ignore and return default
    }

    return prefContinuationIndent // default
  }

  private def prefArrayDeepIndent: Boolean = {
    val option = getCoreFormatterOption(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_EXPRESSIONS_IN_ARRAY_INITIALIZER)
    try {
      return DefaultCodeFormatterConstants.getIndentStyle(option) == DefaultCodeFormatterConstants.INDENT_ON_COLUMN
    } catch {
      case _: IllegalArgumentException => // ignore and return default
    }

    return true
  }

  private def prefCaseIndent: Int = {
    if (DefaultCodeFormatterConstants.TRUE.equals(getCoreFormatterOption(DefaultCodeFormatterConstants.FORMATTER_INDENT_SWITCHSTATEMENTS_COMPARE_TO_SWITCH)))
      return prefBlockIndent
    else
      return 0
  }

  private def prefAssignmentIndent = prefBlockIndent

  private def prefCaseBlockIndent: Int = {
    if (DefaultCodeFormatterConstants.TRUE.equals(getCoreFormatterOption(DefaultCodeFormatterConstants.FORMATTER_INDENT_SWITCHSTATEMENTS_COMPARE_TO_CASES)))
      return prefBlockIndent
    else
      return 0
  }

  private def prefSimpleIndent: Int = {
    if (prefIndentBracesForBlocks && prefBlockIndent == 0)
      return 1
    else return prefBlockIndent
  }

  private def prefBracketIndent: Int = prefBlockIndent

  private def prefMethodDeclDeepIndent: Boolean = {
    val option = getCoreFormatterOption(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_PARAMETERS_IN_METHOD_DECLARATION)
    try {
      return DefaultCodeFormatterConstants.getIndentStyle(option) == DefaultCodeFormatterConstants.INDENT_ON_COLUMN
    } catch {
      case _: IllegalArgumentException => // ignore and return default
    }

    return true
  }

  private def prefMethodDeclIndent: Int = {
    val option = getCoreFormatterOption(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_PARAMETERS_IN_METHOD_DECLARATION)
    try {
      if (DefaultCodeFormatterConstants.getIndentStyle(option) == DefaultCodeFormatterConstants.INDENT_BY_ONE)
        return 1
      else
        return prefContinuationIndent
    } catch {
      case _: IllegalArgumentException => // ignore and return default
    }
    return 1
  }

  private def prefMethodCallDeepIndent: Boolean = {
    val option = getCoreFormatterOption(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_ARGUMENTS_IN_METHOD_INVOCATION)
    try {
      return DefaultCodeFormatterConstants.getIndentStyle(option) == DefaultCodeFormatterConstants.INDENT_ON_COLUMN
    } catch {
      case _: IllegalArgumentException => // ignore and return default
    }
    return false // sensible default
  }

  private def prefMethodCallIndent: Int = {
    val option = getCoreFormatterOption(DefaultCodeFormatterConstants.FORMATTER_ALIGNMENT_FOR_ARGUMENTS_IN_METHOD_INVOCATION)
    try {
      if (DefaultCodeFormatterConstants.getIndentStyle(option) == DefaultCodeFormatterConstants.INDENT_BY_ONE)
        return 1
      else
        return prefContinuationIndent
    } catch {
      case _: IllegalArgumentException => // ignore and return default
    }

    return 1 // sensible default
  }

  private def prefParenthesisDeepIndent = false

  private def prefParenthesisIndent = prefContinuationIndent

  private def prefBlockIndent: Int = {
    val option = getCoreFormatterOption(DefaultCodeFormatterConstants.FORMATTER_INDENT_STATEMENTS_COMPARE_TO_BLOCK)
    if (DefaultCodeFormatterConstants.FALSE.equals(option))
      return 0

    return 1 // sensible default
  }

  private def prefMethodBodyIndent: Int = {
    if (DefaultCodeFormatterConstants.FALSE.equals(getCoreFormatterOption(DefaultCodeFormatterConstants.FORMATTER_INDENT_STATEMENTS_COMPARE_TO_BODY)))
      return 0

    return 1 // sensible default
  }

  private def prefTypeIndent: Int = {
    val option = getCoreFormatterOption(DefaultCodeFormatterConstants.FORMATTER_INDENT_BODY_DECLARATIONS_COMPARE_TO_TYPE_HEADER)
    if (DefaultCodeFormatterConstants.FALSE.equals(option))
      return 0

    return 1 // sensible default
  }

  private def prefIndentBracesForBlocks =
    DefaultCodeFormatterConstants.NEXT_LINE_SHIFTED.equals(getCoreFormatterOption(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_BLOCK))

  private def prefIndentBracesForArrays =
    DefaultCodeFormatterConstants.NEXT_LINE_SHIFTED.equals(getCoreFormatterOption(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_ARRAY_INITIALIZER))

  private def prefIndentBracesForMethods =
    DefaultCodeFormatterConstants.NEXT_LINE_SHIFTED.equals(getCoreFormatterOption(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_METHOD_DECLARATION))

  private def prefIndentBracesForTypes =
    DefaultCodeFormatterConstants.NEXT_LINE_SHIFTED.equals(getCoreFormatterOption(DefaultCodeFormatterConstants.FORMATTER_BRACE_POSITION_FOR_TYPE_DECLARATION))

  private def prefContinuationIndent: Int = {
    Exception.failAsValue(classOf[Exception])(2) {
      Integer.parseInt(getCoreFormatterOption(DefaultCodeFormatterConstants.FORMATTER_CONTINUATION_INDENTATION))
    }
  }

  private def hasGenerics: Boolean = true

  /** The indentation accumulated by <code>findReferencePosition</code>. */
  private var fIndent: Int = 0

  /**
   * The absolute (character-counted) indentation offset for special cases
   * (method defs, array initializers)
   */
  private var fAlign: Int = 0

  /** The stateful scanposition for the indentation methods. */
  private var fPosition: Int = 0

  /** The previous position. */
  private var fPreviousPos: Int = 0

  /** The most recent token. */
  private var fToken: Int = 0

  /** The line of <code>fPosition</code>. */
  private var fLine: Int = 0

  /**
   * Computes the indentation at the reference point of <code>position</code>.
   *
   * @param offset the offset in the document
   * @return a String which reflects the indentation at the line in which the
   *         reference position to <code>offset</code> resides, or <code>null</code>
   *         if it cannot be determined
   */
  def getReferenceIndentation(offset: Int): StringBuffer = {
    getReferenceIndentation(offset, false)
  }

  /**
   * Computes the indentation at the reference point of <code>position</code>.
   *
   * @param offset the offset in the document
   * @param assumeOpeningBrace <code>true</code> if an opening brace should be assumed
   * @return a String which reflects the indentation at the line in which the
   *         reference position to <code>offset</code> resides, or <code>null</code>
   *         if it cannot be determined
   */
  private def getReferenceIndentation(offset: Int, assumeOpeningBrace: Boolean): StringBuffer = {

    val unit =
      findReferencePosition(offset, if (assumeOpeningBrace) Symbols.TokenLBRACE else peekChar(offset))

    // if we were unable to find anything, return null
    if (unit == JavaHeuristicScanner.NOT_FOUND)
      return null

    return getLeadingWhitespace(unit)

  }

  /**
   * Computes the indentation at <code>offset</code>.
   *
   * @param offset the offset in the document
   * @return a String which reflects the correct indentation for the line in
   *         which offset resides, or <code>null</code> if it cannot be
   *         determined
   */
  def computeIndentation(offset: Int): StringBuffer = computeIndentation(offset, false)

  /**
   * Computes the indentation at <code>offset</code>.
   *
   * @param offset the offset in the document
   * @param assumeOpeningBrace <code>true</code> if an opening brace should be assumed
   * @return a String which reflects the correct indentation for the line in
   *         which offset resides, or <code>null</code> if it cannot be
   *         determined
   */
  def computeIndentation(offset: Int, assumeOpeningBrace: Boolean): StringBuffer = {

    val reference = getReferenceIndentation(offset, assumeOpeningBrace)

    // handle special alignment
    if (fAlign != JavaHeuristicScanner.NOT_FOUND) {
      try {
        // a special case has been detected.
        val line = document.getLineInformationOfOffset(fAlign)
        val lineOffset = line.getOffset()
        return createIndent(lineOffset, fAlign, false)
      } catch {
        case _: BadLocationException => return null
      }
    }

    if (reference == null)
      return null

    // add additional indent
    return createReusingIndent(reference, fIndent)
  }

  /**
   * Computes the length of a <code>CharacterSequence</code>, counting
   * a tab character as the size until the next tab stop and every other
   * character as one.
   *
   * @param indent the string to measure
   * @return the visual length in characters
   */
  private def computeVisualLength(indent: CharSequence): Int = {
    val tabSize = prefTabSize
    var length = 0
    for (i <- 0 until indent.length) {
      val ch = indent.charAt(i)
      ch match {
        case '\t' =>
          if (tabSize > 0) {
            val reminder = length % tabSize
            length += tabSize - reminder
          }
        case ' ' =>
          length += 1

        case _ =>
        // Nothing to do
      }
    }
    return length
  }

  /**
   * Strips any characters off the end of <code>reference</code> that exceed
   * <code>indentLength</code>.
   *
   * @param reference the string to measure
   * @param indentLength the maximum visual indentation length
   * @return the stripped <code>reference</code>
   */
  private def stripExceedingChars(reference: StringBuffer, indentLength: Int): StringBuffer = {
    val tabSize = prefTabSize
    var measured = 0
    var chars = reference.length()
    var i = 0
    while (i < min(indentLength, chars)) {
      val ch = reference.charAt(i)
      ch match {
        case '\t' =>
          if (tabSize > 0) {
            val reminder = measured % tabSize
            measured += tabSize - reminder
          }
        case ' ' =>
          measured += 1

        case _ =>
        // Nothing to do
      }
      i += 1
    }
    val deleteFrom = if (measured > indentLength) i - 1 else i

    return reference.delete(deleteFrom, chars);
  }

  /**
   * Returns the indentation of the line at <code>offset</code> as a
   * <code>StringBuffer</code>. If the offset is not valid, the empty string
   * is returned.
   *
   * @param offset the offset in the document
   * @return the indentation (leading whitespace) of the line in which
   *       <code>offset</code> is located
   */
  private def getLeadingWhitespace(offset: Int): StringBuffer = {
    val indent = new StringBuffer()
    try {
      val line = document.getLineInformationOfOffset(offset)
      val lineOffset = line.getOffset()
      val nonWS = scanner.findNonWhitespaceForwardInAnyPartition(lineOffset, lineOffset + line.getLength())
      indent.append(document.get(lineOffset, nonWS - lineOffset))
      return indent
    } catch {
      case _: BadLocationException => // Nothing to do - we return the indent by default
    }
    return indent
  }

  /**
   * Creates an indentation string of the length indent - start, consisting of
   * the content in <code>document</code> in the range [start, indent),
   * with every character replaced by a space except for tabs, which are kept
   * as such.
   * <p>
   * If <code>convertSpaceRunsToTabs</code> is <code>true</code>, every
   * run of the number of spaces that make up a tab are replaced by a tab
   * character. If it is not set, no conversion takes place, but tabs in the
   * original range are still copied verbatim.
   * </p>
   *
   * @param start the start of the document region to copy the indent from
   * @param indent the exclusive end of the document region to copy the indent
   *        from
   * @param convertSpaceRunsToTabs whether to convert consecutive runs of
   *        spaces to tabs
   * @return the indentation corresponding to the document content specified
   *         by <code>start</code> and <code>indent</code>
   */
  private def createIndent(start: Int, indent: Int, convertSpaceRunsToTabs: Boolean): StringBuffer = {
    val convertTabs = prefUseTabs && convertSpaceRunsToTabs
    val tabLen = prefTabSize
    val ret = new StringBuffer()
    try {
      var spaces = 0
      for (i <- start until indent) {
        val ch = document.getChar(i)
        if (ch == '\t') {
          ret.append('\t')
          spaces = 0
        } else if (convertTabs) {
          spaces += 1
          if (spaces == tabLen) {
            ret.append('\t')
            spaces = 0
          }
        } else {
          ret.append(' ')
        }
      }

      // remainder
      (0 until spaces).foreach(_ => ret.append(' '))

    } catch {
      case _: BadLocationException =>
    }

    return ret
  }

  /**
   * Creates a string with a visual length of the given
   * <code>indentationSize</code>.
   *
   * @param buffer the original indent to reuse if possible
   * @param additional the additional indentation units to add or subtract to
   *        reference
   * @return the modified <code>buffer</code> reflecting the indentation
   *         adapted to <code>additional</code>
   */
  private def createReusingIndent(buffer: StringBuffer, additional: Int): StringBuffer = {
    val refLength = computeVisualLength(buffer)
    val addLength = prefIndentationSize * additional // may be < 0
    val totalLength = Math.max(0, refLength + addLength)

    // copy the reference indentation for the indent up to the last tab
    // stop within the maxCopy area
    val minLength = Math.min(totalLength, refLength)
    val tabSize = prefTabSize
    val maxCopyLength = if (tabSize > 0) minLength - minLength % tabSize else minLength // maximum indent to copy
    stripExceedingChars(buffer, maxCopyLength)

    // add additional indent
    val missing = totalLength - maxCopyLength
    val (tabs, spaces) =
      if (JavaCore.SPACE.equals(prefTabChar)) {
        (0, missing)
      } else if (JavaCore.TAB.equals(prefTabChar)) {
        if (tabSize > 0)
          (missing / tabSize, missing % tabSize)
        else
          (0, missing)
      } else if (DefaultCodeFormatterConstants.MIXED.equals(prefTabChar)) {
        if (tabSize > 0)
          (missing / tabSize, missing % tabSize)
        else
          (0, missing)
      } else {
        Assert.isTrue(false)
        return null
      }
    (0 until tabs).foreach(_ => buffer.append('\t'))
    (0 until spaces).foreach(_ => buffer.append(' '))
    return buffer
  }

  /**
   * Returns the reference position regarding to indentation for <code>offset</code>,
   * or <code>NOT_FOUND</code>. This method calls
   * {@link #findReferencePosition(int, int) findReferencePosition(offset, nextChar)} where
   * <code>nextChar</code> is the next character after <code>offset</code>.
   *
   * @param offset the offset for which the reference is computed
   * @return the reference statement relative to which <code>offset</code>
   *         should be indented, or {@link JavaHeuristicScanner#NOT_FOUND}
   */
  def findReferencePosition(offset: Int): Int = findReferencePosition(offset, peekChar(offset))

  /**
   * Peeks the next char in the document that comes after <code>offset</code>
   * on the same line as <code>offset</code>.
   *
   * @param offset the offset into document
   * @return the token symbol of the next element, or TokenEOF if there is none
   */
  private def peekChar(offset: Int): Int = {
    if (offset < document.getLength()) {
      try {
        val line = document.getLineInformationOfOffset(offset)
        val lineOffset = line.getOffset()
        val next = scanner.nextToken(offset, lineOffset + line.getLength())
        return next
      } catch {
        case _: BadLocationException =>
        // Ignore this exception
      }
    }
    return Symbols.TokenEOF
  }

  /**
   * Returns the reference position regarding to indentation for <code>position</code>,
   * or <code>NOT_FOUND</code>.
   *
   * <p>If <code>peekNextChar</code> is <code>true</code>, the next token after
   * <code>offset</code> is read and taken into account when computing the
   * indentation. Currently, if the next token is the first token on the line
   * (i.e. only preceded by whitespace), the following tokens are specially
   * handled:
   * <ul>
   *  <li><code>switch</code> labels are indented relative to the switch block</li>
   *  <li>opening curly braces are aligned correctly with the introducing code</li>
   *  <li>closing curly braces are aligned properly with the introducing code of
   *    the matching opening brace</li>
   *  <li>closing parenthesis' are aligned with their opening peer</li>
   *  <li>the <code>else</code> keyword is aligned with its <code>if</code>, anything
   *    else is aligned normally (i.e. with the base of any introducing statements).</li>
   *  <li>if there is no token on the same line after <code>offset</code>, the indentation
   *    is the same as for an <code>else</code> keyword</li>
   * </ul>
   *
   * @param offset the offset for which the reference is computed
   * @param nextToken the next token to assume in the document
   * @return the reference statement relative to which <code>offset</code>
   *         should be indented, or {@link JavaHeuristicScanner#NOT_FOUND}
   */
  def findReferencePosition(offset: Int, nextToken: Int): Int = {
    var danglingElse = false
    var unindent = false
    var indent = false
    var matchBrace = false
    var matchParen = false
    var matchCase = false
    var matchBracket = false

    // account for un-indentation characters already typed in, but after position
    // if they are on a line by themselves, the indentation gets adjusted
    // accordingly
    //
    // also account for a dangling else
    if (offset < document.getLength()) {
      try {
        val line = document.getLineInformationOfOffset(offset)
        val lineOffset = line.getOffset()
        val prevPos = Math.max(offset - 1, 0)
        val isFirstTokenOnLine = document.get(lineOffset, prevPos + 1 - lineOffset).trim().length() == 0
        val prevToken = scanner.previousToken(prevPos, JavaHeuristicScanner.UNBOUND)
        val bracelessBlockStart = scanner.isBracelessBlockStart(prevPos, JavaHeuristicScanner.UNBOUND)

        nextToken match {
          case Symbols.TokenELSE =>
            danglingElse = true

          case Symbols.TokenCASE |
            Symbols.TokenDEFAULT =>
            if (isFirstTokenOnLine)
              matchCase = true

          case Symbols.TokenLBRACE => // for opening-brace-on-new-line style
            if (bracelessBlockStart && !prefIndentBracesForBlocks)
              unindent = true
            else if ((prevToken == Symbols.TokenCOLON || prevToken == Symbols.TokenEQUAL || prevToken == Symbols.TokenRBRACKET) && !prefIndentBracesForArrays)
              unindent = true
            else if (!bracelessBlockStart && prefIndentBracesForMethods)
              indent = true

          case Symbols.TokenRBRACE => // closing braces get unindented
            if (isFirstTokenOnLine)
              matchBrace = true

          case Symbols.TokenRPAREN =>
            if (isFirstTokenOnLine)
              matchParen = true

          case Symbols.TokenRBRACKET =>
            if (isFirstTokenOnLine)
              matchBracket = true

          case _ =>
          // Nothing to do
        }
      } catch {
        case _: BadLocationException =>
        // Ignore this exception
      }
    } else {
      // don't assume an else could come if we are at the end of file
      danglingElse = false
    }

    val ref = findReferencePosition(offset, danglingElse, matchBrace, matchParen, matchCase, matchBracket)
    if (unindent)
      fIndent -= 1
    if (indent)
      fIndent += 1
    return ref
  }

  /**
   * Returns the reference position regarding to indentation for <code>position</code>,
   * or <code>NOT_FOUND</code>.<code>fIndent</code> will contain the
   * relative indentation (in indentation units, not characters) after the
   * call. If there is a special alignment (e.g. for a method declaration
   * where parameters should be aligned), <code>fAlign</code> will contain
   * the absolute position of the alignment reference in <code>document</code>,
   * otherwise <code>fAlign</code> is set to <code>JavaHeuristicScanner.NOT_FOUND</code>.
   *
   * @param offset the offset for which the reference is computed
   * @param danglingElse whether a dangling else should be assumed at <code>position</code>
   * @param matchBrace whether the position of the matching brace should be
   *            returned instead of doing code analysis
   * @param matchParen whether the position of the matching parenthesis
   *            should be returned instead of doing code analysis
   * @param matchCase whether the position of a switch statement reference
   *            should be returned (either an earlier case statement or the
   *            switch block brace)
   * @return the reference statement relative to which <code>position</code>
   *         should be indented, or {@link JavaHeuristicScanner#NOT_FOUND}
   */
  def findReferencePosition(offset: Int, danglingElse: Boolean, matchBrace: Boolean, matchParen: Boolean, matchCase: Boolean, matchBracket: Boolean): Int = {
    import JavaHeuristicScanner._

    fIndent = 0 // the indentation modification
    fAlign = NOT_FOUND
    fPosition = offset

    // forward cases
    // an unindentation happens sometimes if the next token is special, namely on braces, parens and case labels
    // align braces, but handle the case where we align with the method declaration start instead of
    // the opening brace.
    if (matchBrace) {
      if (skipScope(Symbols.TokenLBRACE, Symbols.TokenRBRACE)) {
        try {
          // align with the opening brace that is on a line by its own
          val lineOffset = document.getLineOffset(fLine)
          if (lineOffset <= fPosition && document.get(lineOffset, fPosition - lineOffset).trim().length() == 0)
            return fPosition
        } catch {
          case _: BadLocationException =>
          // concurrent modification - walk default path
        }
        // if the opening brace is not on the start of the line, skip to the start
        val pos = skipToStatementStart(true, true)
        fIndent = 0 // indent is aligned with reference position
        return pos
      } else {
        // if we can't find the matching brace, the heuristic is to unindent
        // by one against the normal position
        val pos = findReferencePosition(offset, danglingElse, false, matchParen, matchCase, matchBracket)
        fIndent -= 1
        return pos
      }
    }

    // align parenthesis'
    if (matchParen) {
      if (skipScope(Symbols.TokenLPAREN, Symbols.TokenRPAREN))
        return fPosition
      else {
        // if we can't find the matching paren, the heuristic is to unindent
        // by one against the normal position
        val pos = findReferencePosition(offset, danglingElse, matchBrace, false, matchCase, matchBracket)
        fIndent -= 1
        return pos
      }
    }

    // align brackets
    if (matchBracket) {
      if (skipScope(Symbols.TokenLBRACKET, Symbols.TokenRBRACKET))
        return fPosition
      else {
        // if we can't find the matching paren, the heuristic is to unindent
        // by one against the normal position
        val pos = findReferencePosition(offset, danglingElse, matchBrace, false, matchCase, matchBracket)
        fIndent -= 1
        return pos
      }
    }

    // the only reliable way to get case labels aligned (due to many different styles of using braces in a block)
    // is to go for another case statement, or the scope opening brace
    if (matchCase) {
      return matchCaseAlignment
    }

    nextToken
    // TODO: Remove once SI-6011 is fixed
    (fToken: Any) match {
      // check for an arrow token and increase indentation (handles 'case' and closures)
      case Symbols.TokenGREATERTHAN if scanner.previousToken(fPosition - 1, UNBOUND) == Symbols.TokenEQUAL  =>
          handleScopeIntroduction(offset + 1)

      case Symbols.TokenGREATERTHAN |
        Symbols.TokenRBRACKET |
        Symbols.TokenRBRACE =>
        // skip the block and fall through
        // if we can't complete the scope, reset the scan position
        val pos = fPosition
        if (!skipScope)
          fPosition = pos

        return skipToStatementStart(danglingElse, false)

      case Symbols.TokenSEMICOLON =>
        // this is the 90% case: after a statement block
        // the end of the previous statement / block previous.end
        // search to the end of the statement / block before the previous; the token just after that is previous.start
        return skipToStatementStart(danglingElse, false)

      // scope introduction: special treat who special is
      case Symbols.TokenLPAREN |
        Symbols.TokenLBRACE |
        Symbols.TokenLBRACKET =>
        return handleScopeIntroduction(offset + 1)

      case Symbols.TokenEOF =>
        // trap when hitting start of document
        return NOT_FOUND

      case Symbols.TokenEQUAL =>
        // indent assignments
        fIndent = prefAssignmentIndent
        return fPosition

      // indentation for blockless introducers:
      case Symbols.TokenDO |
        Symbols.TokenWHILE |
        Symbols.TokenELSE =>
        fIndent = prefSimpleIndent
        return fPosition

      case Symbols.TokenTRY =>
        return skipToStatementStart(danglingElse, false)

      case Symbols.TokenRPAREN =>
        val line = fLine
        if (skipScope(Symbols.TokenLPAREN, Symbols.TokenRPAREN)) {
          val scope = fPosition
          nextToken
          if (fToken == Symbols.TokenIF || fToken == Symbols.TokenWHILE || fToken == Symbols.TokenFOR) {
            fIndent = prefSimpleIndent
            return fPosition
          }
          fPosition = scope
          if (looksLikeMethodDecl) {
            return skipToStatementStart(danglingElse, false)
          }
          if (fToken == Symbols.TokenCATCH) {
            return skipToStatementStart(danglingElse, false)
          }
          fPosition = scope
          if (looksLikeAnonymousTypeDecl) {
            return skipToStatementStart(danglingElse, false)
          }
        }
        // restore
        fPosition = offset
        fLine = line

        return skipToPreviousListItemOrListStart

      case Symbols.TokenCOMMA =>
        // inside a list of some type
        // easy if there is already a list item before with its own indentation - we just align
        // if not: take the start of the list ( LPAREN, LBRACE, LBRACKET ) and either align or
        // indent by list-indent
        return skipToPreviousListItemOrListStart

      case _ =>
        // inside whatever we don't know about: similar to the list case:
        // if we are inside a continued expression, then either align with a previous line that has indentation
        // or indent from the expression start line (either a scope introducer or the start of the expr).
        return skipToPreviousListItemOrListStart
    }
  }

  /**
   * Skips to the start of a statement that ends at the current position.
   *
   * @param danglingElse whether to indent aligned with the last <code>if</code>
   * @param isInBlock whether the current position is inside a block, which limits the search scope to the next scope introducer
   * @return the reference offset of the start of the statement
   */
  private def skipToStatementStart(danglingElse: Boolean, isInBlock: Boolean): Int = {
    val NOTHING = 0
    val READ_PARENS = 1
    val READ_IDENT = 2
    var mayBeMethodBody = NOTHING
    var isTypeBody = false
    while (true) {
      nextToken

      if (isInBlock) {
        fToken match {
          // exit on all block introducers
          case Symbols.TokenIF |
            Symbols.TokenELSE |
            Symbols.TokenCATCH |
            Symbols.TokenDO |
            Symbols.TokenWHILE |
            Symbols.TokenFINALLY |
            Symbols.TokenFOR |
            Symbols.TokenTRY =>
            return fPosition

          case Symbols.TokenSTATIC =>
            mayBeMethodBody = READ_IDENT // treat static blocks like methods

          case Symbols.TokenSYNCHRONIZED =>
            // if inside a method declaration, use body indentation
            // else use block indentation.
            if (mayBeMethodBody != READ_IDENT)
              return fPosition

          case Symbols.TokenCLASS |
            Symbols.TokenINTERFACE |
            Symbols.TokenENUM =>
            isTypeBody = true

          case Symbols.TokenSWITCH =>
            fIndent = prefCaseIndent
            return fPosition

          case _ =>
          // Nothing to do
        }
      }

      fToken match {
        // scope introduction through: LPAREN, LBRACE, LBRACKET
        // search stop on SEMICOLON, RBRACE, COLON, EOF
        // -> the next token is the start of the statement (i.e. previousPos when backward scanning)
        case Symbols.TokenLPAREN |
          Symbols.TokenLBRACE |
          Symbols.TokenLBRACKET |
          Symbols.TokenSEMICOLON |
          Symbols.TokenEOF =>
          if (isInBlock)
            fIndent = getBlockIndent(mayBeMethodBody == READ_IDENT, isTypeBody)
          // else: fIndent set by previous calls
          return fPreviousPos

        case Symbols.TokenRBRACE =>
          // RBRACE is usually it is the end of a previous block
          val pos = fPreviousPos // store state
          if (!(skipScope && looksLikeArrayInitializerIntro)) {
            if (isInBlock)
              fIndent = getBlockIndent(mayBeMethodBody == READ_IDENT, isTypeBody)
            return pos // it's not - do as with all the above
          }
        // scopes: skip them
        case Symbols.TokenRPAREN |
          Symbols.TokenRBRACKET |
          Symbols.TokenGREATERTHAN =>

          if (fToken == Symbols.TokenRPAREN) {
            if (isInBlock)
              mayBeMethodBody = READ_PARENS
          }

          val pos = fPreviousPos
          if (!skipScope)
            return pos

        // IF / ELSE: align the position after the conditional block with the if
        // so we are ready for an else, except if danglingElse is false
        // in order for this to work, we must skip an else to its if
        case Symbols.TokenIF =>
          if (danglingElse)
            return fPosition

        case Symbols.TokenELSE =>
          // skip behind the next if, as we have that one covered
          val pos = fPosition
          if (!skipNextIF)
            return pos

        case Symbols.TokenDO =>
          // align the WHILE position with its do
          return fPosition

        case Symbols.TokenWHILE =>
          // this one is tricky: while can be the start of a while loop
          // or the end of a do - while
          val pos = fPosition
          if (!hasMatchingDo) {
            // continue searching from the WHILE on
            fPosition = pos
          }
        // else continue searching from the DO on

        case Symbols.TokenIDENT =>
          if (mayBeMethodBody == READ_PARENS)
            mayBeMethodBody = READ_IDENT

        case _ =>
        // keep searching
      }
    }

    // We never reach here
    assert(true)
    return 0
  }

  private def looksLikeArrayInitializerIntro: Boolean = {
    nextToken
    if (fToken == Symbols.TokenEQUAL || skipBrackets) {
      return false // Never return true
    }
    return false
  }

  private def getBlockIndent(isMethodBody: Boolean, isTypeBody: Boolean): Int = {
    if (isTypeBody)
      return prefTypeIndent + (if (prefIndentBracesForTypes) 1 else 0)
    else if (isMethodBody)
      return prefMethodBodyIndent + (if (prefIndentBracesForMethods) 1 else 0)
    else
      return fIndent
  }

  /**
   * Returns as a reference any previous <code>switch</code> labels (<code>case</code>
   * or <code>default</code>) or the offset of the brace that scopes the switch
   * statement. Sets <code>fIndent</code> to <code>prefCaseIndent</code> upon
   * a match.
   *
   * @return the reference offset for a <code>switch</code> label
   */
  @tailrec
  private def matchCaseAlignment: Int = {
    nextToken
    fToken match {
      // invalid cases: another case label or an LBRACE must come before a case
      // -> bail out with the current position
      case Symbols.TokenLPAREN |
        Symbols.TokenLBRACKET |
        Symbols.TokenEOF =>
        return fPosition

      case Symbols.TokenLBRACE =>
        // opening brace of switch statement
        fIndent = prefCaseIndent
        return fPosition

      case Symbols.TokenCASE |
        Symbols.TokenDEFAULT =>
        // align with previous label
        fIndent = 0
        return fPosition

      // scopes: skip them
      case Symbols.TokenRPAREN |
        Symbols.TokenRBRACKET |
        Symbols.TokenRBRACE |
        Symbols.TokenGREATERTHAN =>
        skipScope
        return matchCaseAlignment

      case _ =>
        // keep searching
        return matchCaseAlignment
    }
  }

  /**
   * Returns the reference position for a list element. The algorithm
   * tries to match any previous indentation on the same list. If there is none,
   * the reference position returned is determined depending on the type of list:
   * The indentation will either match the list scope introducer (e.g. for
   * method declarations), so called deep indents, or simply increase the
   * indentation by a number of standard indents. See also {@link #handleScopeIntroduction(int)}.
   *
   * @return the reference position for a list item: either a previous list item
   * that has its own indentation, or the list introduction start.
   */
  private def skipToPreviousListItemOrListStart: Int = {
    var startLine = fLine
    var startPosition = fPosition
    while (true) {
      nextToken

      // if any line item comes with its own indentation, adapt to it
      if (fLine < startLine) {
        try {
          val lineOffset = document.getLineOffset(startLine)
          val bound = Math.min(document.getLength(), startPosition + 1)
          fAlign = scanner.findNonWhitespaceForwardInAnyPartition(lineOffset, bound)
        } catch {
          case _: BadLocationException =>
          // ignore and return just the position
        }
        return startPosition
      }

      fToken match {
        // scopes: skip them
        case Symbols.TokenRPAREN |
          Symbols.TokenRBRACKET |
          Symbols.TokenRBRACE |
          Symbols.TokenGREATERTHAN =>
          skipScope

        // scope introduction: special treat who special is
        case Symbols.TokenLPAREN |
          Symbols.TokenLBRACE |
          Symbols.TokenLBRACKET =>
          return handleScopeIntroduction(startPosition + 1)

        case Symbols.TokenSEMICOLON =>
          return fPosition

        case Symbols.TokenEOF =>
          return 0

        case _ =>
        // Do nothing

      }
    }

    return 0 // Never get here
  }

  /**
   * Skips a scope and positions the cursor (<code>fPosition</code>) on the
   * token that opens the scope. Returns <code>true</code> if a matching peer
   * could be found, <code>false</code> otherwise. The current token when calling
   * must be one out of <code>Symbols.TokenRPAREN</code>, <code>Symbols.TokenRBRACE</code>,
   * and <code>Symbols.TokenRBRACKET</code>.
   *
   * @return <code>true</code> if a matching peer was found, <code>false</code> otherwise
   */
  private def skipScope: Boolean = {
    fToken match {
      case Symbols.TokenRPAREN =>
        return skipScope(Symbols.TokenLPAREN, Symbols.TokenRPAREN)
      case Symbols.TokenRBRACKET =>
        return skipScope(Symbols.TokenLBRACKET, Symbols.TokenRBRACKET)
      case Symbols.TokenRBRACE =>
        return skipScope(Symbols.TokenLBRACE, Symbols.TokenRBRACE)
      case Symbols.TokenGREATERTHAN =>
        val storedPosition = fPosition
        val storedToken = fToken
        nextToken

        var isGenericStarter = false
        if (fToken == Symbols.TokenIDENT) {
          try {
            isGenericStarter = !JavaHeuristicScanner.isGenericStarter(getTokenContent)
          } catch {
            case _: BadLocationException =>
              return false
          }
        }

        if ((fToken == Symbols.TokenIDENT && !isGenericStarter) ||
          fToken == Symbols.TokenQUESTIONMARK ||
          fToken == Symbols.TokenGREATERTHAN) {

          if (skipScope(Symbols.TokenLESSTHAN, Symbols.TokenGREATERTHAN))
            return true
        }
        // <> are harder to detect - restore the position if we fail
        fPosition = storedPosition
        fToken = storedToken
        return false

      case _ =>
        Assert.isTrue(false)
        return false
    }
  }

  /**
   * Returns the contents of the current token.
   *
   * @return the contents of the current token
   * @throws BadLocationException if the indices are out of bounds
   * @since 3.1
   */
  private def getTokenContent = new DocumentCharacterIterator(document, fPosition, fPreviousPos)

  /**
   * Handles the introduction of a new scope. The current token must be one out
   * of <code>Symbols.TokenLPAREN</code>, <code>Symbols.TokenLBRACE</code>,
   * and <code>Symbols.TokenLBRACKET</code>. Returns as the reference position
   * either the token introducing the scope or - if available - the first
   * java token after that.
   *
   * <p>Depending on the type of scope introduction, the indentation will align
   * (deep indenting) with the reference position (<code>fAlign</code> will be
   * set to the reference position) or <code>fIndent</code> will be set to
   * the number of indentation units.
   * </p>
   *
   * @param bound the bound for the search for the first token after the scope
   * introduction.
   * @return the indent
   */
  private def handleScopeIntroduction(bound: Int): Int = {
    fToken match {
      // scope introduction: special treat who special is
      case Symbols.TokenLPAREN =>
        val pos = fPosition // store

        // special: method declaration deep indentation
        if (looksLikeMethodDecl) {
          if (prefMethodDeclDeepIndent)
            return setFirstElementAlignment(pos, bound)
          else {
            fIndent = prefMethodDeclIndent;
            return pos
          }
        } else {
          fPosition = pos
          if (looksLikeMethodCall) {
            if (prefMethodCallDeepIndent)
              return setFirstElementAlignment(pos, bound)
            else {
              fIndent = prefMethodCallIndent
              return pos
            }
          } else if (prefParenthesisDeepIndent)
            return setFirstElementAlignment(pos, bound)
        }

        // normal: return the parenthesis as reference
        fIndent = prefParenthesisIndent
        return pos

      case Symbols.TokenLBRACE =>
        val pos = fPosition // store

        // special: array initializer
        if (looksLikeArrayInitializerIntro)
          if (prefArrayDeepIndent)
            return setFirstElementAlignment(pos, bound)
          else
            fIndent = prefArrayIndent
        else
          fIndent = prefBlockIndent

        // normal: skip to the statement start before the scope introducer
        // opening braces are often on differently ending indents than e.g. a method definition
        if (looksLikeArrayInitializerIntro && !prefIndentBracesForArrays
          || !prefIndentBracesForBlocks) {
          fPosition = pos // restore
          return skipToStatementStart(true, true) // set to true to match the first if
        } else {
          return pos
        }

      case Symbols.TokenLBRACKET =>
        val pos = fPosition // store
        //        return setFirstElementAlignment(pos, bound)
        //        fIndent = prefMethodCallIndent
        /*
        // special: method declaration deep indentation
        if (prefArrayDimensionsDeepIndent) {
          return setFirstElementAlignment(pos, bound)
        }

        // normal: return the bracket as reference
         */
        fIndent = prefBracketIndent
        return pos // restore

      // a '=>', could be a case pattern or a closure
      case Symbols.TokenGREATERTHAN =>
        fIndent = prefBracketIndent
        return fPosition

      case _ =>
        Assert.isTrue(false)
        return -1 // dummy
    }
  }

  /**
   * Sets the deep indent offset (<code>fAlign</code>) to either the offset
   * right after <code>scopeIntroducerOffset</code> or - if available - the
   * first Java token after <code>scopeIntroducerOffset</code>, but before
   * <code>bound</code>.
   *
   * @param scopeIntroducerOffset the offset of the scope introducer
   * @param bound the bound for the search for another element
   * @return the reference position
   */
  private def setFirstElementAlignment(scopeIntroducerOffset: Int, bound: Int): Int = {
    val firstPossible = scopeIntroducerOffset + 1; // align with the first position after the scope intro
    fAlign = scanner.findNonWhitespaceForwardInAnyPartition(firstPossible, bound)
    if (fAlign == JavaHeuristicScanner.NOT_FOUND)
      fAlign = firstPossible
    return fAlign;
  }

  /**
   * Skips over the next <code>if</code> keyword. The current token when calling
   * this method must be an <code>else</code> keyword. Returns <code>true</code>
   * if a matching <code>if</code> could be found, <code>false</code> otherwise.
   * The cursor (<code>fPosition</code>) is set to the offset of the <code>if</code>
   * token.
   *
   * @return <code>true</code> if a matching <code>if</code> token was found, <code>false</code> otherwise
   */
  private def skipNextIF: Boolean = {
    Assert.isTrue(fToken == Symbols.TokenELSE)

    while (true) {
      nextToken
      fToken match {
        // scopes: skip them
        case Symbols.TokenRPAREN |
          Symbols.TokenRBRACKET |
          Symbols.TokenRBRACE |
          Symbols.TokenGREATERTHAN =>

          skipScope

        case Symbols.TokenIF =>
          // found it, return
          return true

        case Symbols.TokenELSE =>
          // recursively skip else-if blocks
          skipNextIF

        // shortcut scope starts
        case Symbols.TokenLPAREN |
          Symbols.TokenLBRACE |
          Symbols.TokenLBRACKET |
          Symbols.TokenEOF =>
          return false

        case _ =>
        // Nothing to do

      }
    }

    // Will never reach here
    return false
  }

  /**
   * while(condition); is ambiguous when parsed backwardly, as it is a valid
   * statement by its own, so we have to check whether there is a matching
   * do. A <code>do</code> can either be separated from the while by a
   * block, or by a single statement, which limits our search distance.
   *
   * @return <code>true</code> if the <code>while</code> currently in
   *         <code>fToken</code> has a matching <code>do</code>.
   */
  private def hasMatchingDo: Boolean = {
    Assert.isTrue(fToken == Symbols.TokenWHILE)
    nextToken

    if (fToken == Symbols.TokenRBRACE) {
      skipScope
    }
    if (fToken == Symbols.TokenSEMICOLON) {
      skipToStatementStart(false, false)
      return fToken == Symbols.TokenDO
    }
    return false
  }

  /**
   * Skips brackets if the current token is a RBRACKET. There can be nothing
   * but whitespace in between, this is only to be used for <code>[]</code> elements.
   *
   * @return <code>true</code> if a <code>[]</code> could be scanned, the
   *         current token is left at the LBRACKET.
   */
  private def skipBrackets: Boolean = {
    if (fToken == Symbols.TokenRBRACKET) {
      nextToken
      if (fToken == Symbols.TokenLBRACKET) {
        return true
      }
    }
    return false
  }

  /**
   * Reads the next token in backward direction from the heuristic scanner
   * and sets the fields <code>fToken, fPreviousPosition</code> and <code>fPosition</code>
   * accordingly.
   */
  private def nextToken: Unit = {
    nextToken(fPosition)
  }

  /**
   * Reads the next token in backward direction of <code>start</code> from
   * the heuristic scanner and sets the fields <code>fToken, fPreviousPosition</code>
   * and <code>fPosition</code> accordingly.
   *
   * @param start the start offset from which to scan backwards
   */
  private def nextToken(start: Int) = {
    fToken = scanner.previousToken(start - 1, JavaHeuristicScanner.UNBOUND)
    fPreviousPos = start
    fPosition = scanner.getPosition() + 1
    try {
      fLine = document.getLineOfOffset(fPosition)
    } catch {
      case e: BadLocationException =>
        fLine = -1
    }
  }

  /**
   * Returns <code>true</code> if the current tokens look like a method
   * declaration header (i.e. only the return type and method name). The
   * heuristic calls <code>nextToken</code> and expects an identifier
   * (method name) and a type declaration (an identifier with optional
   * brackets) which also covers the visibility modifier of constructors; it
   * does not recognize package visible constructors.
   *
   * @return <code>true</code> if the current position looks like a method
   *         declaration header.
   */
  private def looksLikeMethodDecl: Boolean = {

    /*
     * TODO This heuristic does not recognize package private constructors
     * since those do have neither type nor visibility keywords.
     * One option would be to go over the parameter list, but that might
     * be empty as well, or not typed in yet - hard to do without an AST...
     */

    nextToken
    if (fToken == Symbols.TokenIDENT) { // method name
      do nextToken
      while (skipBrackets) // optional brackets for array valued return types

      return fToken == Symbols.TokenIDENT; // return type name

    }
    return false
  }

  /**
   * Returns <code>true</code> if the current tokens look like an anonymous type declaration
   * header (i.e. a type name (potentially qualified) and a new keyword). The heuristic calls
   * <code>nextToken</code> and expects a possibly qualified identifier (type name) and a new
   * keyword
   *
   * @return <code>true</code> if the current position looks like a anonymous type declaration
   *         header.
   */
  private def looksLikeAnonymousTypeDecl: Boolean = {
    nextToken
    if (fToken == Symbols.TokenIDENT) { // type name
      nextToken
      while (fToken == Symbols.TokenOTHER) { // dot of qualification
        nextToken
        if (fToken != Symbols.TokenIDENT) // qualificating name
          return false
        nextToken
      }
      return fToken == Symbols.TokenNEW
    }
    return false
  }

  /**
   * Returns <code>true</code> if the current tokens look like a method
   * call header (i.e. an identifier as opposed to a keyword taking parenthesized
   * parameters such as <code>if</code>).
   * <p>The heuristic calls <code>nextToken</code> and expects an identifier
   * (method name).
   *
   * @return <code>true</code> if the current position looks like a method call
   *         header.
   */
  private def looksLikeMethodCall: Boolean = {
    // TODO [5.0] add awareness for constructor calls with generic types: new ArrayList<String>()
    nextToken
    return fToken == Symbols.TokenIDENT // method name
  }

  /**
   * Scans tokens for the matching opening peer. The internal cursor
   * (<code>fPosition</code>) is set to the offset of the opening peer if found.
   *
   * @param openToken the opening peer token
   * @param closeToken the closing peer token
   * @return <code>true</code> if a matching token was found, <code>false</code>
   *         otherwise
   */
  private def skipScope(openToken: Int, closeToken: Int): Boolean = {
    var depth = 1

    while (true) {
      nextToken

      if (fToken == closeToken) {
        depth += 1
      } else if (fToken == openToken) {
        depth -= 1
        if (depth == 0)
          return true
      } else if (fToken == Symbols.TokenEOF) {
        return false
      }
    }

    return false // Never reaches here
  }
}

