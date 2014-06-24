package org.scalaide.ui.internal.preferences

import org.eclipse.ui.IWorkbenchPreferencePage
import org.eclipse.jface.preference.PreferencePage
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.ui.IWorkbench
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridLayout
import org.eclipse.jface.viewers.ListViewer
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.jface.viewers.IStructuredContentProvider
import org.eclipse.jface.viewers.Viewer
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.core.internal.project.BundledScalaInstallation
import org.scalaide.core.internal.project.MultiBundleScalaInstallation
import org.scalaide.ui.internal.project.ScalaInstallationUIProviders

class InstalledScalasPreferencePage extends PreferencePage with IWorkbenchPreferencePage with ScalaInstallationUIProviders {

  def title = "Scala Installation"

  def createContents(parent: Composite): Control = {
    val composite = new Composite(parent, SWT.NONE)

    composite.setLayout(new GridLayout(2, false))

    val list = new ListViewer(composite)
    list.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
    list.setContentProvider(new ContentProvider())
    list.setLabelProvider(new LabelProvider)
    list.setInput(ScalaInstallation.availableInstallations)

    val buttons = new Composite(composite, SWT.NONE)
    buttons.setLayoutData(new GridData(SWT.BEGINNING, SWT.TOP, false, true))
    buttons.setLayout(new FillLayout(SWT.VERTICAL))

    val button1 = new Button(buttons, SWT.PUSH)
    button1.setText("Add")
    button1.setEnabled(false)

    composite
  }

  def init(workbench: IWorkbench): Unit = {}

}