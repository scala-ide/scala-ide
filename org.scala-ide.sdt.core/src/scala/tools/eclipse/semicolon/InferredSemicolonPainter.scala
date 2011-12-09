package scala.tools.eclipse.semicolon

import scala.math.min
import scala.tools.eclipse.util.EclipseUtils._
import scalariform.lexer._
import scalariform.parser._
import scalariform.utils.Utils._
import org.eclipse.swt.widgets.Display
import org.eclipse.swt.custom._
import org.eclipse.swt.events._
import org.eclipse.swt.graphics._
import org.eclipse.jface.text._

object InferredSemicolonPainter {

  val SEMICOLON_COLOUR = new Color(Display.getDefault, new RGB(160, 160, 160))

}
/**
 * Paints fake semicolon glyphs where a statement separator has been inferred.
 *
 * For responsiveness, the location of inferred semicolons are calculated after a certain delay since the last keystroke.
 *
 * @see org.eclipse.jface.text.WhitespaceCharacterPainter
 */
class InferredSemicolonPainter(textViewer: ITextViewer with ITextViewerExtension5)
  extends IPainter with PaintListener with IDocumentListener {

  import InferredSemicolonPainter._

  private val typingDelayHelper: TypingDelayHelper = new TypingDelayHelper

  private var installed = false

  private var inferredSemis: List[Token] = Nil

  def dispose() {
    val doc = textViewer.getDocument
    if (installed && doc != null)
      doc.removeDocumentListener(this)
    typingDelayHelper.stop()
  }

  def paint(reason: Int) {
    val doc = textViewer.getDocument
    if (!installed && doc != null) {
      textWidget.addPaintListener(this)
      doc.addDocumentListener(this)
      installed = true
      inferredSemis = findInferredSemis
      textWidget.redraw()
    } else if (reason == IPainter.TEXT_CHANGE && true) {
      val lineRegion = doc.getLineInformationOfOffset(textViewer.widgetOffset2ModelOffset(textWidget.getCaretOffset))
      val widgetOffset = textViewer.modelOffset2WidgetOffset(lineRegion.getOffset)
      val charCount = textWidget.getCharCount
      val redrawLength = min(lineRegion.getLength, charCount - widgetOffset)
      if (widgetOffset >= 0 && redrawLength > 0)
        textWidget.redrawRange(widgetOffset, redrawLength, true);
    } else
      textWidget.redraw()
  }

  def deactivate(redraw: Boolean) {
    if (installed) {
      textWidget.removePaintListener(this)
      val document = textViewer.getDocument
      if (document != null)
        document.removeDocumentListener(this)
      installed = false
      if (redraw)
        textWidget.redraw()
    }
  }

  def documentAboutToBeChanged(event: DocumentEvent) {}

  def documentChanged(event: DocumentEvent) {
    inferredSemis = updateInferredSemis(event)
    typingDelayHelper.scheduleCallback {
      inferredSemis = findInferredSemis
      textWidget.redraw()
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
      if (semi.startIndex >= offset)
        semi.copy(offset = semi.offset + change)
      else
        semi
    }
  }

  private def findInferredSemis: List[Token] =
    try {
      val (hiddenTokenInfo, tokens) = ScalaLexer.tokeniseFull(textViewer.getDocument.get)
      InferredSemicolonScalaParser.findSemicolons(tokens.toArray).toList
    } catch {
      case e: ScalaParserException => Nil
    }

  def setPositionManager(manager: IPaintPositionManager) {}

  def paintControl(event: PaintEvent) {
    val startLine = scala.math.max(textWidget.getLineIndex(event.y), 0)
    val endLine = scala.math.min(textWidget.getLineIndex(event.y + event.height - 1) + 1, textWidget.getLineCount - 1)
    if (startLine <= endLine && startLine < textWidget.getLineCount) {
      val modelStart = textViewer.widgetOffset2ModelOffset(textWidget.getOffsetAtLine(startLine))
      val modelEnd = textViewer.widgetOffset2ModelOffset(textWidget.getOffsetAtLine(endLine))
      drawCharRange(event.gc, modelStart, modelEnd)
    }
  }

  private def textWidget = textViewer.getTextWidget

  private def getBestPositionToDraw(token: Token, document: IDocument): Int = {
    var pos = token.startIndex
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
      val baseline = textWidget.getBaseline(widgetOffset)
      val fontMetrics = gc.getFontMetrics
      val fontBaseline = fontMetrics.getAscent + fontMetrics.getLeading
      val baselineDelta = baseline - fontBaseline
      val pos = textWidget.getLocationAtOffset(widgetOffset)
      gc.setForeground(SEMICOLON_COLOUR)
      gc.drawString(";", pos.x, pos.y + baselineDelta, true)
    }
  }

}

