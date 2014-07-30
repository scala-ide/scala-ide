package org.scalaide.ui.internal.preferences

import org.eclipse.jface.preference.PreferencePage
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Text
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.scalaide.core.ScalaPlugin
import org.scalaide.ui.internal.editor.hover.ScalaHover
import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer

/** This class is referenced through plugin.xml */
class HoverPreferencePage extends PreferencePage with IWorkbenchPreferencePage {

  private val prefStore = ScalaPlugin.prefStore
  private var cssArea: Text = _

  override def createContents(parent: Composite): Control = {
    val base = new Composite(parent, SWT.NONE)
    base.setLayout(new GridLayout(1, true))

    val description = new Label(base, SWT.NONE)
    description.setText("The style of the hovers is specified by the following CSS code, which can be freely edited:")

    cssArea = new Text(base, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL)
    cssArea.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
    cssArea.setText(prefStore.getString(ScalaHover.ScalaHoverStyleSheetId))
    base
  }

  override def init(workbench: IWorkbench): Unit = ()

  override def performOk(): Boolean = {
    val css = cssArea.getText()
    prefStore.setValue(ScalaHover.ScalaHoverStyleSheetId, css)
    super.performOk()
  }

  override def performDefaults(): Unit = {
    super.performDefaults()
    cssArea.setText(ScalaHover.DefaultScalaHoverStyleSheet)
  }

}

/** This class is referenced through plugin.xml */
class HoverPreferenceInitializer extends AbstractPreferenceInitializer {

  override def initializeDefaultPreferences(): Unit = {
    ScalaPlugin.prefStore.setDefault(ScalaHover.ScalaHoverStyleSheetId, ScalaHover.DefaultScalaHoverStyleSheet)
  }
}