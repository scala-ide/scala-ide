package scala.tools.eclipse.formatter

import org.eclipse.jdt.internal.corext.fix.CodeFormatFix
import scala.tools.eclipse.contribution.weaving.jdt.ui.javaeditor.formatter.IFormatterCleanUpProvider
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jface.text.{ Document, TextUtilities };
import org.eclipse.jdt.ui.cleanup.ICleanUpFix
import scalariform.formatter.ScalaFormatter
import org.eclipse.text.edits.{ TextEdit => JFaceTextEdit, _ }
import scalariform.utils.TextEdit
import org.eclipse.jdt.core.refactoring.CompilationUnitChange

class ScalaFormatterCleanUpProvider extends IFormatterCleanUpProvider {

  def createCleanUp(cu: ICompilationUnit): ICleanUpFix = {
    val contents = cu.getBuffer.getContents
    val document = new Document(contents)
    val lineDelimiter = TextUtilities.getDefaultLineDelimiter(document)
    val edits = ScalaFormatter.formatAsEdits(cu.getSource, FormatterPreferencePage.getPreferences, Some(lineDelimiter))
    val resultEdit = new MultiTextEdit
    for (TextEdit(start, length, replacement) <- edits)
      resultEdit.addChild(new ReplaceEdit(start, length, replacement))
    val change = new CompilationUnitChange("", cu)
    change.setEdit(resultEdit);
    new CodeFormatFix(change)
  }

}