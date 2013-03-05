/*
 * Copyright 2005-2013 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse
package ui

import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Shell
import  org.eclipse.jface.text.{IInformationControlCreator, AbstractReusableInformationControlCreator,
                              DefaultInformationControl}
import org.eclipse.jface.action.ToolBarManager
import org.eclipse.jdt.ui.PreferenceConstants
import org.eclipse.jdt.internal.ui.text.java.hover.JavadocHover
import org.eclipse.jface.internal.text.html.BrowserInformationControl
import org.eclipse.jdt.internal.ui.JavaPlugin

object BrowserControlCreator {
  def apply(): IInformationControlCreator = {
    val inner = new AbstractReusableInformationControlCreator {
      def doCreateInformationControl(parent: Shell) =
        if (BrowserInformationControl.isAvailable(parent))
          new BrowserInformationControl(parent, PreferenceConstants.APPEARANCE_JAVADOC_FONT, null: ToolBarManager)
        else
          new DefaultInformationControl(parent, JavaPlugin.getAdditionalInfoAffordanceString())
    }
    new JavadocHover.HoverControlCreator(inner, true)
  }
}
