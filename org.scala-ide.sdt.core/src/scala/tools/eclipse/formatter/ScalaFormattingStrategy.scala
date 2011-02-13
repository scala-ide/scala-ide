package scala.tools.eclipse.formatter

import org.eclipse.jface.text.formatter.IFormattingStrategy
import org.eclipse.jface.text.source.ISourceViewer
import scalariform.parser.ScalaParserException
import scalariform.formatter.ScalaFormatter
import scalariform.formatter.preferences.FormattingPreferences
import scalariform.utils.TextEdit
import org.eclipse.text.undo.DocumentUndoManagerRegistry
import org.eclipse.text.edits.{ TextEdit => JFaceTextEdit, _ }
import org.eclipse.jface.text.{ IDocument, TextUtilities }

class ScalaFormattingStrategy(val sourceViewer: ISourceViewer) extends IFormattingStrategy {

  def format(content: String, isLineStart: Boolean, indentation: String, positions: Array[Int]): String = {
    format(sourceViewer.getDocument)
    null
  }

  private def format(document: IDocument) {
    val source = document.get
    val lineDelimiter = Option(TextUtilities.getDefaultLineDelimiter(document))
    try {
      val edits = ScalaFormatter.formatAsEdits(source, FormatterPreferencePage.getPreferences, lineDelimiter)

      val undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document)
      undoManager.beginCompoundChange()
      val eclipseEdit = new MultiTextEdit
      for (TextEdit(start, length, replacement) <- edits)
        eclipseEdit.addChild(new ReplaceEdit(start, length, replacement))
      new TextEditProcessor(document, eclipseEdit, JFaceTextEdit.NONE).performEdits
      undoManager.endCompoundChange()

    } catch {
      case _: ScalaParserException =>
      case e => throw e
    }
  }

  def formatterStarts(initialIndentation: String) {}

  def formatterStops() {}

}