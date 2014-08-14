package org.scalaide.ui.internal.editor.hover

import org.eclipse.jface.action.ToolBarManager
import org.eclipse.jface.internal.text.html.BrowserInformationControl
import org.eclipse.jface.text.AbstractReusableInformationControlCreator
import org.eclipse.jface.text.DefaultInformationControl
import org.eclipse.jface.text.IInformationControl
import org.eclipse.jface.text.IInformationControlCreator
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Shell
import org.eclipse.ui.editors.text.EditorsUI


/**
 * A hover control that should be used once the mouse hovers over an area. It
 * displays an affordance string that tells users how to focus the hover.
 * Once the hover is focused, `focusedCreator` is used to display the content of
 * the hover.
 * As long as this control is not focused, it automatically disappears once the
 * mouse leaves the hover area.
 *
 * `fontId` references a font in the preference store that is used by this
 * component.
 */
class HoverControlCreator(focusedCreator: IInformationControlCreator, fontId: String)
    extends AbstractReusableInformationControlCreator {

  def doCreateInformationControl(parent: Shell): IInformationControl = {
    val tas = EditorsUI.getTooltipAffordanceString()

    if (BrowserInformationControl.isAvailable(parent)) {
      new BrowserInformationControl(parent, fontId, tas) with BrowserControlAdditions {
        override def getInformationPresenterControlCreator() = focusedCreator
      }
    }
    else
      new DefaultInformationControl(parent, tas)
  }
}

/**
 * This control creator should be used once an user focused the hover. A
 * focused hover does not automatically disappear when the mouse leaves the
 * hover area, instead it needs to be closed explicitly.
 *
 * `fontId` references a font in the preference store that is used by this
 * component.
 */
class FocusedControlCreator(fontId: String)
    extends AbstractReusableInformationControlCreator {

  override def doCreateInformationControl(parent: Shell): IInformationControl = {
    if (BrowserInformationControl.isAvailable(parent)) {
      val tbm = new ToolBarManager(SWT.FLAT)
      new BrowserInformationControl(parent, fontId, tbm) with BrowserControlAdditions
    }
    else
      new DefaultInformationControl(parent, /* isResizeable */ true)
  }
}
