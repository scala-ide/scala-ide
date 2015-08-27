package org.scalaide.ui.internal.preferences

import scala.collection.mutable.ListBuffer

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.ProjectScope
import org.eclipse.jdt.core.IJavaProject
import org.eclipse.jdt.internal.ui.preferences.PreferencesMessages
import org.eclipse.jface.preference.FieldEditor
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Link
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.ui.dialogs.PreferencesUtil
import org.eclipse.ui.dialogs.PropertyPage
import org.scalaide.util.eclipse.SWTUtils._

import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swt.MigLayout

/**
 * Contains common definitions for preference and property pages that want to
 * display field editors.
 */
trait FieldEditors extends PropertyPage with IWorkbenchPreferencePage {

  protected final var isWorkbenchPage: Boolean = false
  protected final var allEnableDisableControls: Set[Control] = Set()
  protected final val fieldEditors: ListBuffer[FieldEditor] = ListBuffer()

  def useProjectSpecifcSettingsKey: String

  def initUnderlyingPreferenceStore(pluginId: String, pluginPrefStore: IPreferenceStore): Unit = {
    setPreferenceStore(getElement match {
      case project: IProject     ⇒ new PropertyStore(new ProjectScope(project), pluginId)
      case project: IJavaProject ⇒ new PropertyStore(new ProjectScope(project.getProject), pluginId)
      case _                     ⇒ pluginPrefStore
    })
  }

  def pageId: String

  def addNewFieldEditorWrappedInComposite[T <: FieldEditor](parent: Composite)(f: Composite ⇒ T): T = {
    val composite = new Composite(parent, SWT.NONE)
    val fieldEditor = f(composite)

    fieldEditor.setPreferenceStore(getPreferenceStore)
    fieldEditor.load

    if (isWorkbenchPage)
      composite.setLayoutData(new CC().grow.wrap)
    else
      composite.setLayoutData(new CC().spanX(2).grow.wrap)

    fieldEditor
  }

  def mkMainControl(parent: Composite)(f: Composite ⇒ Unit): Composite = {
    val control = new Composite(parent, SWT.NONE)
    val rowConstraints = if (isWorkbenchPage)
      new AC().index(0).grow(0).index(1).grow
    else
      new AC().index(0).grow(0).index(1).grow(0).index(2).grow(0).index(3).grow

    control.setLayout(new MigLayout(new LC().insetsAll("0").fill, new AC(), rowConstraints))

    if (!isWorkbenchPage) {
      val projectSpecificButton = new Button(control, SWT.CHECK | SWT.WRAP)
      projectSpecificButton.setText("Enable project specific settings")
      projectSpecificButton.setSelection(getPreferenceStore.getBoolean(useProjectSpecifcSettingsKey))
      projectSpecificButton.addSelectionListener { () ⇒
        val enabled = projectSpecificButton.getSelection
        getPreferenceStore.setValue(useProjectSpecifcSettingsKey, enabled)
        allEnableDisableControls foreach { _.setEnabled(enabled) }
      }
      projectSpecificButton.setLayoutData(new CC)

      val link = new Link(control, SWT.NONE)
      link.setText("<a>"+PreferencesMessages.PropertyAndPreferencePage_useworkspacesettings_change+"</a>")
      link.addSelectionListener { () ⇒
        PreferencesUtil.createPreferenceDialogOn(getShell, pageId, Array(pageId), null).open()
      }
      link.setLayoutData(new CC().alignX("right").wrap)

      val horizontalLine = new Label(control, SWT.SEPARATOR | SWT.HORIZONTAL)
      horizontalLine.setLayoutData(new CC().spanX(2).grow.wrap)
    }

    f(control)

    if (!isWorkbenchPage) {
      val enabled = getPreferenceStore.getBoolean(useProjectSpecifcSettingsKey)
      allEnableDisableControls foreach { _.setEnabled(enabled) }
    }
    control
  }

  override def performDefaults() = {
    super.performDefaults()
    fieldEditors.foreach(_.loadDefault)
  }

  override def performOk() = {
    super.performOk()
    fieldEditors.foreach(_.store)
    true
  }

  override def init(workbench: IWorkbench): Unit = {
    isWorkbenchPage = true
  }

}
