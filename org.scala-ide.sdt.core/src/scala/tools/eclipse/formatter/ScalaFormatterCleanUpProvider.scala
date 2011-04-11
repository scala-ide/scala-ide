package scala.tools.eclipse.formatter

import org.eclipse.jdt.internal.corext.fix.CodeFormatFix
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.refactoring.CompilationUnitChange
import org.eclipse.jdt.ui.cleanup.ICleanUpFix
import org.eclipse.jface.text.{ Document, TextUtilities }
import org.eclipse.text.edits.{ TextEdit => JFaceTextEdit, _ }
import scalariform.formatter.ScalaFormatter
import scalariform.parser.ScalaParserException
import scalariform.utils.TextEdit
import scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor.formatter.IFormatterCleanUpProvider
import scala.tools.eclipse.util.EclipseUtils._
import org.eclipse.jdt.internal.ui.javaeditor.DocumentAdapter

class ScalaFormatterCleanUpProvider extends IFormatterCleanUpProvider {

  def createCleanUp(cu: ICompilationUnit): ICleanUpFix = {
    val document = cu.getBuffer match {
      case adapter: DocumentAdapter => adapter.getDocument
      case _ => new Document(cu.getBuffer.getContents)
    }
    val lineDelimiter = TextUtilities.getDefaultLineDelimiter(document)

    val preferences = FormatterPreferences.getPreferences(cu.getJavaProject.getProject)
    val edits =
      try ScalaFormatter.formatAsEdits(cu.getSource, preferences, Some(lineDelimiter))
      catch { case e: ScalaParserException => return null }

    val multiEdit = new MultiTextEdit
    multiEdit.addChildren(edits map asEclipseTextEdit toArray)
    val change = new CompilationUnitChange("Formatting", cu)
    change.setEdit(multiEdit)
    new CodeFormatFix(change)
  }

}