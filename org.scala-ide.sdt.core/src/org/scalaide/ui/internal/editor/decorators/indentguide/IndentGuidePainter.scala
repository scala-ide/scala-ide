package org.scalaide.ui.internal.editor.decorators.indentguide

import org.eclipse.jface.preference.PreferenceConverter
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.swt.SWT
import org.eclipse.swt.events.PaintEvent
import org.eclipse.swt.graphics.Color
import org.eclipse.swt.graphics.GC
import org.eclipse.swt.widgets.Display
import org.scalaide.ui.internal.editor.decorators.EditorPainter
import org.scalaide.ui.internal.preferences.EditorPreferencePage

/**
 * Contains the UI-related code of the indent guide component.
 *
 * It tries to paint the guides only when necessary. This means:
 * - Only the area that is shown in the editor obtains guide markers. This is the
 *   case when a new file is opened, when the editor loses and retrieves back the
 *   focus, when it is saved or when scrolling happens.
 * - On text change, only the current line is redrawn.
 *
 * This component watches the preference store to find out whether its configuration
 * has changed.
 */
class IndentGuidePainter(viewer: ISourceViewer)
    extends EditorPainter(viewer, EditorPreferencePage.INDENT_GUIDE_ENABLE)
    with IndentGuideGenerator {

  private object config {
    val LineStyle = SWT.LINE_DOT
    val LineWidth = 1
    /**
     * Offset to move the guides to the right. This achieves that the guide
     * and the caret do not completely overlay
     */
    val GuideShift = 2
  }

  /** The width of a space in pixel */
  private val spaceWidth = withGC { _.getAdvanceWidth(' ') }

  private var color: Color = _

  override def paintByReason(reason: Int): Unit = {}

  override def paintByEvent(e: PaintEvent): Unit = {
    val (y, h, gc) = (e.y, e.height, e.gc)
    val startLine = widget.getLineIndex(y)
    val endLine = widget.getLineIndex(y + h - 1)

    gc.setLineStyle(config.LineStyle)
    gc.setLineWidth(config.LineWidth)
    gc.setForeground(color)

    def drawLine(guide: Guide) = {
      val p = widget.getLocationAtOffset(widget.getOffsetAtLine(guide.line))
      val x = p.x + guide.column * spaceWidth + config.GuideShift
      val h = widget.getLineHeight(guide.line)
      gc.drawLine(x, p.y, x, p.y + h)
    }

    guidesOfRange(startLine, endLine) foreach drawLine
  }

  override def dispose(): Unit = {
    if (color != null)
      color.dispose()
  }

  override def textOfLine(line: Int): String = widget.getLine(line)
  override def lineCount: Int = widget.getLineCount()
  override def indentWidth: Int = widget.getTabs()

  override def loadPreferences(): Unit = {
    val rgb = PreferenceConverter.getColor(store, EditorPreferencePage.INDENT_GUIDE_COLOR)
    if (color != null)
      color.dispose()
    color = new Color(Display.getCurrent(), rgb)
  }

  private def withGC[A](f: GC => A): A = {
    val gc = new GC(widget)
    val res = f(gc)
    gc.dispose()
    res
  }

}

/**
 * Contains the UI-less logic of the indent guide component.
 *
 * The algorithm used to calculate the needed indent guides is based on heuristics
 * because there are some ambigous cases which can only be determined
 * correctly by anlayzing the whole file semantically. Because this has to run
 * in the UI-Thread a semantic analysis is not an option.
 *
 * These are the main points for the algorithm:
 * - When the line is not empty indent until non whitespace text is found, but
 *   stop one indent width before.
 * - When the line is empty is has to found out if the line is inside a block
 *   (e.g. a function) or not (e.g. a class body). In the latter case indent
 *   guides should never occur.
 * - There exists another case where no guidance should happen. In
 *
 *     def f(i: Int) =
 *       0
 *
 *   the rhs should be guided, but not the line after. One way to detect this
 *   case is to use a heuristic that checks if a rhs ends with a brace or similar
 *   symbols and if this is not the case all lines with whitespace that occur
 *   after the end of the rhs (which is the last line that contains non whitespace
 *   text) should not be guided.
 * - When indentation in a single line changes it could happen that the guides of
 *   some other lines which contain only whitespace have to be invalidated. One
 *   possible case is
 *
 *     def f: Unit = {
 *
 *     def g = 0
 *     }
 *
 *   where `g` is on the wrong indentaton depth. After increasing its indentation,
 *   the guides of the line before have to be renewed as well.
 * - Multi line comments are guided as well.
 * - The first character of each line doesn't get a guide.
 */
trait IndentGuideGenerator {
  /** The first index of `line` is 0, `column` represents the number of whitespace */
  case class Guide(line: Int, column: Int)

  /** This assumes that the first line has index 0 */
  def textOfLine(line: Int): String
  /** The number of lines of a document */
  def lineCount: Int
  /** The number of characters of one indent level */
  def indentWidth: Int

  def guidesOfRange(startLine: Int, endLine: Int): Seq[Guide] = {

    /* indentation depth in number of characters */
    def indentDepth(text: String) = {
      val (sum, _) = text.takeWhile(c => c == ' ' || c == '\t').foldLeft((0, 0)) {
        case ((sum, len), c) =>
          val reminder = indentWidth - len % indentWidth
          if (c == ' ') (sum + 1, len) else (sum + reminder, len + reminder)
      }
      sum
    }

    def decreaseFrom(line: Int) =
      Iterator.iterate(line)(_ - 1).takeWhile(_ > 0)

    def increaseFrom(line: Int) =
      Iterator.iterate(line)(_ + 1).takeWhile(_ < lineCount)

    def iterate(iter: Iterator[Int], f: Int => Seq[Guide])(p: Int => Boolean) =
      iter.find(p).fold(Seq[Guide]())(f)

    def calcGuides(line: Int) = {
      val startLineDepth = indentDepth(textOfLine(line - 1))

      def calcGuides(endLine: Int) = {
        val endLineDepth = indentDepth(textOfLine(endLine))

        def isProbablyClosingBlock =
          textOfLine(endLine).matches("[ \t]*[})\\]].*")

        def guidesOfRange(end: Int) =
          for (line <- line to endLine - 1; i <- indentWidth to end by indentWidth)
            yield Guide(line, i)

        def forNextDepth =
          guidesOfRange(startLineDepth)

        def forSameDepth =
          guidesOfRange(endLineDepth - (if (isProbablyClosingBlock) 0 else indentWidth))

        if (startLineDepth < endLineDepth)
          forNextDepth
        else if (endLineDepth > 0)
          forSameDepth
        else
          Nil
      }

      if (startLineDepth == 0)
        Nil
      else
        iterate(increaseFrom(line), calcGuides) { line =>
          textOfLine(line).trim().nonEmpty
        }
    }

    def guidesForNonEmptyLine(line: Int, text: String) =
      for (i <- indentWidth until indentDepth(text) by indentWidth)
        yield Guide(line, i)

    def guidesForEmptyLines(line: Int) =
      iterate(decreaseFrom(line), calcGuides) { line =>
        textOfLine(line - 1).trim().nonEmpty
      }

    def guidesForLine(line: Int) = {
      val text = textOfLine(line)
      if (text.trim().nonEmpty)
        guidesForNonEmptyLine(line, text)
      else
        guidesForEmptyLines(line)
    }

    startLine to endLine flatMap guidesForLine
  }
}
