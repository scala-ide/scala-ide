package org.scalaide.debug.internal.ui

import org.eclipse.jface.fieldassist.IControlContentAdapter
import org.eclipse.jface.fieldassist.IControlContentAdapter2
import org.eclipse.swt.custom.StyledText
import org.eclipse.swt.graphics.Point
import org.eclipse.swt.graphics.Rectangle
import org.eclipse.swt.widgets.Control

/**
 * Implementation of content adapter adapted and adjusted for StyledText. It's similar to TextContentAdapter.
 * ProposalAdapter uses high level Control class in swt so given object must be explicitly cast to StyledText.
 */
class StyledTextContentAdapter extends IControlContentAdapter with IControlContentAdapter2 {

  implicit def control2Text(control: Control) = control.asInstanceOf[StyledText]

  override def getControlContents(control: Control): String = control.getText()

  override def setControlContents(control: Control, text: String, cursorPosition: Int) {
    control.setText(text)
    control.setSelection(cursorPosition, cursorPosition)
  }

  /**
   * Inserts remaining text from the chosen proposal and moves cursor to the end of it
   */
  override def insertControlContents(control: Control, text: String, cursorPosition: Int) {
    val selection: Point = control.getSelection()
    control.insert(text)
    control.setSelection(selection.x + text.length(), selection.x + text.length())
  }

  override def getCursorPosition(control: Control): Int = control.getCaretOffset()

  override def getInsertionBounds(control: Control): Rectangle = {
    val text: StyledText = control
    val bounds = text.getBlockSelectionBounds()
    bounds.x -= text.getHorizontalPixel() // current position - horizontal scroll offset
    bounds.y -= text.getTopPixel() // current position - vertical scroll offset
    bounds.height = text.getLineHeight() + 4 // will be used as vertical shift
    // width won't be used at all; to define width and height of open window set explicitly ContentProposalAdapter's popupSize
    bounds
  }

  override def setCursorPosition(control: Control, position: Int): Unit = control.setSelection(new Point(position, position))

  override def getSelection(control: Control) = control.getSelection()

  override def setSelection(control: Control, range: Point) = control.setSelection(range)
}
