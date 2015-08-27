package org.scalaide.ui.internal.preferences

import org.eclipse.jface.preference.FieldEditor
import org.eclipse.swt.SWT
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.ui.dialogs.PropertyPage

import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swt.MigLayout

/**
 * Contains common definitions for preference and property pages that want to
 * display field editors.
 */
trait FieldEditors extends PropertyPage with IWorkbenchPreferencePage {

  private var _isWorkbenchPage = false

  protected var allEnableDisableControls = Set[Control]()
  protected val fieldEditors = collection.mutable.ListBuffer[FieldEditor]()

  def isWorkbenchPage: Boolean =
    _isWorkbenchPage

  def isWorkbenchPage_=(isWorkbenchPage: Boolean): Unit =
    _isWorkbenchPage = isWorkbenchPage

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

    f(control)
    allEnableDisableControls foreach { _.setEnabled(true) }
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
