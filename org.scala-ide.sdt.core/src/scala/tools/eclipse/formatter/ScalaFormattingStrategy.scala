package scala.tools.eclipse.formatter

import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.IAdaptable
import org.eclipse.jface.text._
import org.eclipse.jface.text.TextUtilities.getDefaultLineDelimiter
import org.eclipse.jface.text.formatter._
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.text.undo.DocumentUndoManagerRegistry
import org.eclipse.text.edits.{ TextEdit => EclipseTextEdit, _ }
import org.eclipse.ui.texteditor.ITextEditor
import scalariform.formatter.ScalaFormatter
import scalariform.formatter.preferences._
import scalariform.parser.ScalaParserException
import scalariform.utils.TextEdit
import scala.tools.eclipse.properties.PropertyStore
import scala.tools.eclipse.ScalaPlugin
import scala.tools.eclipse.util.EclipseUtils._
import org.eclipse.core.resources.IResource

class ScalaFormattingStrategy(val editor: ITextEditor) extends IFormattingStrategy with IFormattingStrategyExtension {

  private var document: IDocument = _

  private var regionOpt: Option[IRegion] = None

  def formatterStarts(context: IFormattingContext) {
    this.document = context.getProperty(FormattingContextProperties.CONTEXT_MEDIUM).asInstanceOf[IDocument]
    this.regionOpt = Option(context.getProperty(FormattingContextProperties.CONTEXT_REGION).asInstanceOf[IRegion])
  }

  def format() {
    val preferences = FormatterPreferences.getPreferences(getProject)
    var edits =
      try ScalaFormatter.formatAsEdits(document.get, preferences, Some(getDefaultLineDelimiter(document)))
      catch { case _: ScalaParserException => return }

    val (offset, length) = expandToWholeLines(regionOpt match {
      case Some(region) => (region.getOffset, region.getLength)
      case None => (0, document.getLength)
    })
    val formattingRegion = new Position(offset, length)

    val eclipseEdits = edits.collect {
      // We filter down to the edits that intersect the selected region, except those that
      // exceed the selection, because they have a habit of messing with the indentation of subsequent statements.
      case TextEdit(position, len, replacement) if formattingRegion.includes(position + len) =>
        new ReplaceEdit(position, len, replacement)
    }
    applyEdits(eclipseEdits)
  }

  private def expandToWholeLines(offsetAndLength: (Int, Int)): (Int, Int) = {
    val (offset, length) = offsetAndLength
    var current = offset
    while (current >= 0 && document(current) != '\n')
      current -= 1
    assert(current == -1 || document(current) == '\n')
    val newOffset = current + 1

    current = offset + length
    while (current < document.getLength && document(current) != '\n' && document(current) != '\r')
      current += 1
    while (current < document.getLength && (document(current) == '\n' || document(current) == '\r'))
      current += 1
    val newLength = current - newOffset
    (newOffset, newLength)
  }

  private def applyEdits(edits: List[EclipseTextEdit]) {
    val multiEdit = new MultiTextEdit
    multiEdit.addChildren(edits.toArray)

    val undoManager = DocumentUndoManagerRegistry.getDocumentUndoManager(document)
    undoManager.beginCompoundChange()
    new TextEditProcessor(document, multiEdit, EclipseTextEdit.NONE).performEdits
    undoManager.endCompoundChange()
  }

  def formatterStops() {
    this.document = null
    this.regionOpt = None
  }

  private def getProject = editor.getEditorInput.asInstanceOf[IAdaptable].adaptTo[IResource].getProject

  def format(content: String, isLineStart: Boolean, indentation: String, positions: Array[Int]): String = null

  def formatterStarts(initialIndentation: String) {}

}