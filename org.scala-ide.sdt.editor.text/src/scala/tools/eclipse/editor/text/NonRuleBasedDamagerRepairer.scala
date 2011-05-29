package scala.tools.eclipse
package editor.text

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITypedRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.TextAttribute;
import org.eclipse.jface.text.TextPresentation;
import org.eclipse.jface.text.presentation.IPresentationDamager;
import org.eclipse.jface.text.presentation.IPresentationRepairer;
import org.eclipse.core.runtime.Assert;
import org.eclipse.swt.custom.StyleRange;

/**
 * @param defaultTextAttribute The default text attribute if non is returned as data by the current token
 */
class NonRuleBasedDamagerRepairer(val defaultTextAttribute: TextAttribute) extends IPresentationDamager with IPresentationRepairer {
  Assert.isNotNull(defaultTextAttribute)

  /** The document this object works on */
  private var fDocument: IDocument = _

  /**
   * @see IPresentationRepairer#setDocument(IDocument)
   */
  def setDocument(document: IDocument) {
    fDocument = document
  }

  /**
   * Returns the end offset of the line that contains the specified offset or
   * if the offset is inside a line delimiter, the end offset of the next line.
   *
   * @param offset the offset whose line end offset must be computed
   * @return the line end offset for the given offset
   * @exception BadLocationException if offset is invalid in the current document
   */
  protected def endOfLineOf(offset: Int): Int = {

    val info = fDocument.getLineInformationOfOffset(offset)
    if (offset <= info.getOffset() + info.getLength()) {
      info.getOffset() + info.getLength()
    } else {
      val line = fDocument.getLineOfOffset(offset)
      try {
        val info = fDocument.getLineInformation(line + 1)
        info.getOffset() + info.getLength()
      } catch {
        case x: BadLocationException => fDocument.getLength()
      }
    }
  }

  /**
   * @see IPresentationDamager#getDamageRegion(ITypedRegion, DocumentEvent, boolean)
   */
  def getDamageRegion(partition: ITypedRegion, event: DocumentEvent, documentPartitioningChanged: Boolean): IRegion = {
    var back: IRegion = partition
    if (!documentPartitioningChanged) {
      try {

        val info = fDocument.getLineInformationOfOffset(event.getOffset());
        val start = math.max(partition.getOffset(), info.getOffset());

        var end = event.getOffset() + Option(event.getText()).map(_.length).getOrElse(event.getLength())

        if (info.getOffset() <= end
          && end <= info.getOffset() + info.getLength()) {
          // optimize the case of the same line
          end = info.getOffset() + info.getLength();
        } else {
          end = endOfLineOf(end);
        }

        end =
          math.min(
            partition.getOffset() + partition.getLength(),
            end);
        back = new Region(start, end - start)

      } catch {
        case x: BadLocationException => //ignore
      }
    }

    back
  }

  /**
   * @see IPresentationRepairer#createPresentation(TextPresentation, ITypedRegion)
   */
  def createPresentation(
    presentation: TextPresentation,
    region: ITypedRegion) {
    addRange(
      presentation,
      region.getOffset(),
      region.getLength(),
      defaultTextAttribute)
  }

  /**
   * Adds style information to the given text presentation.
   *
   * @param presentation the text presentation to be extended
   * @param offset the offset of the range to be styled
   * @param length the length of the range to be styled
   * @param attr the attribute describing the style of the range to be styled
   */
  protected def addRange(presentation: TextPresentation, offset: Int, length: Int, attr: TextAttribute) {
    if (attr ne null)
      presentation.addStyleRange(new StyleRange(offset, length, attr.getForeground(), attr.getBackground(), attr.getStyle()))
  }
}