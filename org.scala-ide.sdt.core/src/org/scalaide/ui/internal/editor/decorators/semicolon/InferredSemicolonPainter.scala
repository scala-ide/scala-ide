package org.scalaide.ui.internal.editor.decorators.semicolon

import scala.math.min
import org.scalaide.util.internal.eclipse.EclipseUtils._
import scalariform.lexer._
import scalariform.parser._
import org.eclipse.swt.widgets.Display
import org.eclipse.swt.events._
import org.eclipse.swt.graphics._
import org.eclipse.jface.text._
import org.scalaide.ui.internal.editor.decorators.EditorPainter
import org.eclipse.jface.text.source.ISourceViewer
import org.scalaide.ui.internal.preferences.EditorPreferencePage

object InferredSemicolonPainter {

  val SEMICOLON_COLOR = new Color(Display.getDefault, new RGB(160, 160, 160))

}
/**
 * Paints fake semicolon glyphs where a statement separator has been inferred.
 *
 * For responsiveness, the location of inferred semicolons are calculated after a certain delay since the last keystroke.
 *
 * @see org.eclipse.jface.text.WhitespaceCharacterPainter
 */
class InferredSemicolonPainter(textViewer: ISourceViewer with ITextViewerExtension5)
    extends EditorPainter(textViewer, EditorPreferencePage.P_SHOW_INFERRED_SEMICOLONS) with IDocumentListener {

  import InferredSemicolonPainter._

  private val typingDelayHelper: TypingDelayHelper = new TypingDelayHelper

  private var inferredSemis: List[Token] = if (isPainterEnabled) findInferredSemis else Nil

  textViewer.getDocument().addDocumentListener(this)

  override def dispose(): Unit = {
    Option(textViewer.getDocument()) foreach (_.removeDocumentListener(this))
    typingDelayHelper.stop()
  }

  override def loadPreferences(): Unit = {}

  override def paintByReason(reason: Int): Unit = {
    val doc = textViewer.getDocument

    if (reason == IPainter.TEXT_CHANGE) {
      val lineRegion = doc.getLineInformationOfOffset(textViewer.widgetOffset2ModelOffset(widget.getCaretOffset))
      val widgetOffset = textViewer.modelOffset2WidgetOffset(lineRegion.getOffset)
      val charCount = widget.getCharCount
      val redrawLength = min(lineRegion.getLength, charCount - widgetOffset)
      if (widgetOffset >= 0 && redrawLength > 0)
        widget.redrawRange(widgetOffset, redrawLength, true)
    }
  }

  def documentAboutToBeChanged(event: DocumentEvent) {}

  def documentChanged(event: DocumentEvent) {
    if (isPainterEnabled) {
      inferredSemis = updateInferredSemis(event)
      typingDelayHelper.scheduleCallback {
        inferredSemis = findInferredSemis
        widget.redraw()
      }
    }
  }

  /**
   * Shift semi tokens the appropriate amount if they occur after the site of a document change. (This is a heuristic -- the real
   * positions are recalculated the no-typing delay.)
   */
  private def updateInferredSemis(event: DocumentEvent): List[Token] = {
    val offset = event.getOffset
    val change = event.getText.size - event.getLength
    inferredSemis.map { semi =>
      if (semi.offset >= offset)
        semi.copy(offset = semi.offset + change)
      else
        semi
    }
  }

  private def findInferredSemis: List[Token] =
    try {
      val tokens = ScalaLexer.tokenise(textViewer.getDocument.get)
      InferredSemicolonScalaParser.findSemicolons(tokens.toArray).toList
    } catch {
      case e: ScalaParserException => Nil
    }

  override def paintByEvent(event: PaintEvent): Unit = {
    val startLine = scala.math.max(widget.getLineIndex(event.y), 0)
    val endLine = scala.math.min(widget.getLineIndex(event.y + event.height - 1) + 1, widget.getLineCount - 1)

    if (startLine <= endLine && startLine < widget.getLineCount) {
      val modelStart = textViewer.widgetOffset2ModelOffset(widget.getOffsetAtLine(startLine))
      val modelEnd = textViewer.widgetOffset2ModelOffset(widget.getOffsetAtLine(endLine))
      drawCharRange(event.gc, modelStart, modelEnd)
    }
  }

  private def getBestPositionToDraw(token: Token, document: IDocument): Int = {
    var pos = token.offset
    while (pos < document.getLength)
      document(pos) match {
        // A good place to draw is a space before a single-line comment
        case ' ' if pos + 2 < document.getLength && document(pos + 1) == '/' && document(pos + 2) == '/' =>
          return pos
        // Failing that, the end of the line
        case '\n' | '\r' =>
          return pos
        case _ =>
          pos += 1
      }
    document.getLength - 1
  }

  private def drawCharRange(gc: GC, start: Int, end: Int) =
    for (token <- inferredSemis) {
      val documentOffset = getBestPositionToDraw(token, textViewer.getDocument)
      val visible = documentOffset >= start && documentOffset <= end
      if (visible)
        drawSemicolon(gc, documentOffset)
    }

  private def drawSemicolon(gc: GC, modelOffset: Int) {
    val widgetOffset = textViewer.modelOffset2WidgetOffset(modelOffset)
    if (widgetOffset >= 0) {
      val baseline = widget.getBaseline(widgetOffset)
      val fontMetrics = gc.getFontMetrics
      val fontBaseline = fontMetrics.getAscent + fontMetrics.getLeading
      val baselineDelta = baseline - fontBaseline
      val pos = widget.getLocationAtOffset(widgetOffset)
      gc.setForeground(SEMICOLON_COLOR)
      gc.drawString(";", pos.x, pos.y + baselineDelta, true)
    }
  }

}

