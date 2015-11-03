package org.scalaide.ui.internal.editor.autoedits

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.text.DefaultIndentLineAutoEditStrategy
import org.eclipse.jface.text.DocumentCommand
import org.eclipse.jface.text.IDocument
import org.scalaide.core.internal.formatter.FormatterPreferences._
import org.scalaide.core.internal.statistics.Features
import org.scalaide.ui.internal.preferences.EditorPreferencePage

import scalariform.formatter.preferences._

class AutoIndentStrategy(prefStore: IPreferenceStore) extends DefaultIndentLineAutoEditStrategy {

  override def customizeDocumentCommand(doc: IDocument, cmd: DocumentCommand): Unit = {
    val isAutoIndentEnabled = prefStore.getBoolean(
      EditorPreferencePage.P_ENABLE_AUTO_INDENT_ON_TAB)

    cmd.text match {
      case "\t" if isAutoIndentEnabled => indentOnTab(doc, cmd, indentWithTabs, tabSize)
      case "\t" => cmd.text = oneIndent(indentWithTabs, tabSize)
      case _ =>
    }
  }

  protected def indentWithTabs: Boolean =
    prefStore.getBoolean(IndentWithTabs.eclipseKey)

  protected def tabSize: Int =
    prefStore.getInt(IndentSpaces.eclipseKey)

  /**
   * Computes one indentation step which is the tab size (i.e. the number
   * of spaces displayed for a tab character).
   */
  protected def oneIndent(indentWithTabs: Boolean, tabSize: Int): String =
    if (indentWithTabs) "\t" else " " * tabSize

  /**
   * Returns the indentation of a given line.
   */
  protected def indentOfLine(doc: IDocument, line: Int): String = {
    val region = doc.getLineInformation(line)
    val begin = region.getOffset()
    val endOfWhitespace = findEndOfWhiteSpace(doc, begin, begin + region.getLength())
    doc.get(begin, endOfWhitespace - begin)
  }

  /**
   * Returns the whitespace prefix (indentation), the rest of the line for the
   * given offset and also the rest of the line after the caret position.
   */
  protected def breakLine(doc: IDocument, offset: Int): (String, String, String) = {
    // indent up to the previous line
    val lineInfo = doc.getLineInformationOfOffset(offset)
    val endOfWS = findEndOfWhiteSpace(doc, lineInfo.getOffset(), offset)
    val indent = doc.get(lineInfo.getOffset, endOfWS - lineInfo.getOffset)
    val rest = doc.get(endOfWS, lineInfo.getOffset + lineInfo.getLength() - endOfWS)
    val restAfterCaret = doc.get(offset, lineInfo.getOffset() - offset + lineInfo.getLength())
    (indent, rest, restAfterCaret)
  }

  /**
   * Computes the indentation that should be inserted when a tab is pressed and
   * applies it to the command.
   */
  protected def indentOnTab(doc: IDocument, cmd: DocumentCommand, indentWithTabs: Boolean, tabSize: Int): Unit = {
    Features.AutoIndentOnTab.incUsageCounter()
    def textSize(indent: String) =
      indent.map(c => if (c == '\t') tabSize else 1).sum

    val line = doc.getLineOfOffset(cmd.offset)
    val (curLineIndent, rest, restAfterCaret) = breakLine(doc, cmd.offset)
    val prevLineIndent = {
      def decreaseFrom(line: Int) =
        Iterator.iterate(line)(_ - 1).takeWhile(_ > 0)

      val findPrevLine = decreaseFrom(line - 1) dropWhile { line =>
        val r = doc.getLineInformation(line)
        val str = doc.get(r.getOffset(), r.getLength())
        str.trim().isEmpty()
      }
      if (findPrevLine.hasNext) indentOfLine(doc, findPrevLine.next()) else ""
    }

    def copyPreviousLineIndent =
      if (curLineIndent.isEmpty())
        prevLineIndent
      else {
        val len = curLineIndent.length()
        val missingIndentSteps = prevLineIndent.drop(len).grouped(tabSize).size

        if (indentWithTabs)
          Seq.fill(missingIndentSteps)(oneIndent(indentWithTabs, tabSize)).mkString
        else
          prevLineIndent.drop(len)
      }

    val indent =
      if (prevLineIndent == curLineIndent || textSize(prevLineIndent) < textSize(curLineIndent))
        oneIndent(indentWithTabs, tabSize)
      else if (rest != restAfterCaret || restAfterCaret.trim().nonEmpty)
        oneIndent(indentWithTabs, tabSize)
      else
        copyPreviousLineIndent

    cmd.text = indent
  }
}
