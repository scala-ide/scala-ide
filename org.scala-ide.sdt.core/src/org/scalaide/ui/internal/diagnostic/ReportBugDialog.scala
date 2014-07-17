package org.scalaide.ui.internal.diagnostic

import org.eclipse.jface.dialogs.Dialog
import org.eclipse.jface.dialogs.IDialogConstants
import org.eclipse.swt.widgets.{ List => SWTList, _ }
import org.eclipse.swt.layout.RowLayout
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.ui.internal.layout.CellLayout
import org.eclipse.swt.SWT
import org.eclipse.swt.events.ModifyListener
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionListener
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.core.runtime.Platform
import org.scalaide.ui.internal.actions.OpenExternalFile
import org.scalaide.logging.LogManager
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.project.ScalaInstallation


class ReportBugDialog(shell: Shell) extends Dialog(shell) {

  /** Overwritten in order to set the title text. */
  override def configureShell(sh: Shell): Unit = {
    super.configureShell(sh)
    sh.setText("Bug Reporter")
  }

  protected override def isResizable = true

  protected override def createDialogArea(parent: Composite): Control = {
    val control = new Composite(parent, SWT.NONE)
    control.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, true))
    control.setLayout(new GridLayout)

    val group1 = new Group(control, SWT.SHADOW_NONE)
    group1.setText("Installation details")
    group1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true))
    group1.setLayout(new GridLayout(1, false))

    val messageField = new Text(group1, SWT.READ_ONLY | SWT.MULTI | SWT.BORDER)
    messageField.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL))
    messageField.setText(
        s"""|Scala IDE version:
            |        ${ScalaPlugin.plugin.getBundle.getVersion}
            |Scala compiler version:
            |        ${ScalaPlugin.plugin.scalaVer.unparse}
            |Scala library version:
            |        ${ScalaInstallation.platformInstallation.version.unparse}
            |Eclipse version:
            |        ${Platform.getBundle("org.eclipse.platform").getVersion}
            |""".stripMargin)

    val group2 = new Group(control, SWT.SHADOW_NONE)
    group2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))
    // lay out the widgets on the same row
    val rowLayout = new RowLayout(SWT.HORIZONTAL)
    rowLayout.spacing = -3 // remove space between widgets
    group2.setLayout(rowLayout)

    val logFileLink = new Link(group2, SWT.NONE)
    logFileLink.setText("<a>Check</a> the log")
    logFileLink.addListener(SWT.Selection, OpenExternalFile(LogManager.logFile))

    val reportBugLink = new Link(group2, SWT.NONE)
    reportBugLink.setText(s""" and <a href="${ScalaPlugin.IssueTracker}">report a bug</a>.""")
    reportBugLink.addListener(SWT.Selection, new LinkListener())

    control
  }

  protected override def createButtonsForButtonBar(parent: Composite) {
    // create only OK button
    createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true)
  }
}
