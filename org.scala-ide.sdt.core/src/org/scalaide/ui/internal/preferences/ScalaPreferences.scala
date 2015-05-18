package org.scalaide.ui.internal.preferences

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.dialogs.PropertyPage
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Group
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.SWT
import org.scalaide.logging.HasLogger
import org.scalaide.core.IScalaPlugin

class ScalaPreferences extends PropertyPage with IWorkbenchPreferencePage with EclipseSettings
  with ScalaPluginPreferencePage with HasLogger {

  /** Pulls the preference store associated with this plugin */
  override def doGetPreferenceStore(): IPreferenceStore = {
    IScalaPlugin().getPreferenceStore()
  }

  override def init(wb: IWorkbench): Unit = {}

  /** Returns the id of what preference page we use */
  override val eclipseBoxes: List[EclipseSetting.EclipseBox] = Nil

  def createContents(parent: Composite): Control = {
    val composite = {
      //No Outer Composite
      val tmp = new Composite(parent, SWT.NONE)
      val layout = new GridLayout(1, false)
      tmp.setLayout(layout)
      val data = new GridData(GridData.FILL)
      data.grabExcessHorizontalSpace = true
      data.horizontalAlignment = GridData.FILL
      tmp.setLayoutData(data)
      tmp
    }

    eclipseBoxes.foreach(eBox => {
      val group = new Group(composite, SWT.SHADOW_ETCHED_IN)
      group.setText(eBox.name)
      val layout = new GridLayout(3, false)
      group.setLayout(layout)
      val data = new GridData(GridData.FILL)
      data.grabExcessHorizontalSpace = true
      data.horizontalAlignment = GridData.FILL
      group.setLayoutData(data)
      eBox.eSettings.foreach(_.addTo(group))
    })
    composite
  }

  override def performOk = try {
    eclipseBoxes.foreach(_.eSettings.foreach(_.apply()))
    save()
    true
  } catch {
    case ex: Throwable => eclipseLog.error(ex); false
  }

  def updateApply(): Unit = {
    updateApplyButton
  }

  /** Updates the apply button with the appropriate enablement. */
  protected override def updateApplyButton(): Unit = {
    if (getApplyButton != null) {
      if (isValid) {
        getApplyButton.setEnabled(isChanged)
      } else {
        getApplyButton.setEnabled(false)
      }
    }
  }

  def save(): Unit = {
    //Don't let user click "apply" again until a change
    updateApplyButton
  }
}
