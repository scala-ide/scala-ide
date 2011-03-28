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
import org.eclipse.jdt.internal.ui.javaeditor.DocumentAdapter

class ScalaFormatterCleanUpProvider extends IFormatterCleanUpProvider {

  def createCleanUp(cu: ICompilationUnit): ICleanUpFix = {
    val project = cu.getJavaProject.getProject
    val document = cu.getBuffer.asInstanceOf[DocumentAdapter].getDocument
    val lineDelimiter = TextUtilities.getDefaultLineDelimiter(document)
    val edits =
      try ScalaFormatter.formatAsEdits(cu.getSource, FormatterPreferences.getPreferences(project), Some(lineDelimiter))
      catch { case e: ScalaParserException => return null }
    val resultEdit = new MultiTextEdit
    for (TextEdit(start, length, replacement) <- edits)
      resultEdit.addChild(new ReplaceEdit(start, length, replacement))
    val change = new CompilationUnitChange("", cu)
    change.setEdit(resultEdit);
    new CodeFormatFix(change)
  }

}