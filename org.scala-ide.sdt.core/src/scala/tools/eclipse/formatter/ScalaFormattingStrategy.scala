package scala.tools.eclipse.formatter

import org.eclipse.core.resources.IProject
import org.eclipse.jface.preference.IPreferenceStore
import scala.tools.eclipse.properties.PropertyStore
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.jdt.core.IJavaElement
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.ui.texteditor.ITextEditor
import org.eclipse.jface.text.formatter.IFormattingStrategy
import org.eclipse.jface.text.source.ISourceViewer
import scalariform.parser.ScalaParserException
import scalariform.formatter.ScalaFormatter
import scalariform.formatter.preferences.FormattingPreferences
import scalariform.utils.TextEdit
import org.eclipse.text.undo.DocumentUndoManagerRegistry
import org.eclipse.text.edits.{ TextEdit => JFaceTextEdit, _ }
import org.eclipse.jface.text.{ IDocument, TextUtilities }

class ScalaFormattingStrategy(val editor: ITextEditor) extends IFormattingStrategy {

  def format(content: String, isLineStart: Boolean, indentation: String, positions: Array[Int]): String = {
    val project = editor.getEditorInput.asInstanceOf[IAdaptable].getAdapter(classOf[IJavaElement]).asInstanceOf[IJavaElement].getJavaProject.getProject
    val document = editor.getDocumentProvider.getDocument(editor.getEditorInput)
    val source = document.get
    val lineDelimiter = Option(TextUtilities.getDefaultLineDelimiter(document))
    val preferences = FormatterPreferences.getPreferences(project)

    val edits =
      try ScalaFormatter.formatAsEdits(source, preferences, lineDelimiter)
      catch { case _: ScalaParserException => return null }
    applyEdits(document, edits)

    null
  }

  private def applyEdits(document: IDocument, edits: List[TextEdit]) {
    val undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document)
    undoManager.beginCompoundChange()
    val eclipseEdit = new MultiTextEdit
    for (TextEdit(start, length, replacement) <- edits)
      eclipseEdit.addChild(new ReplaceEdit(start, length, replacement))
    new TextEditProcessor(document, eclipseEdit, JFaceTextEdit.NONE).performEdits
    undoManager.endCompoundChange()
  }

  def formatterStarts(initialIndentation: String) {}

  def formatterStops() {}
}