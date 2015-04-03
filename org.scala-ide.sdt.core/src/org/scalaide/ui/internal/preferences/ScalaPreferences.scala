package org.scalaide.ui.internal.preferences

import org.eclipse.jface.preference.PreferencePage
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage

class ScalaPreferences extends PreferencePage with IWorkbenchPreferencePage {
  def createContents(parent: Composite): Control = {
    new Composite(parent, SWT.NONE)
  }

  def init(workbench: IWorkbench): Unit = ()
}
