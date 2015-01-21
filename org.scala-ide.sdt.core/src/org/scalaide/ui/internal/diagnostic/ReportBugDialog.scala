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
import org.scalaide.core.internal.logging.LogManager
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.internal.project.ScalaInstallation.platformInstallation
import org.scalaide.core.SdtConstants
import org.scalaide.core.internal.ScalaPlugin

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

    val cacheEntries = ScalaPlugin().classLoaderStore.entries
    val entries = cacheEntries.map(e => s"Compiler v. ${e._1.version.unparse}(${e._1.compiler.classJar})")

    messageField.setText(
        s"""|Scala IDE version:
            |        ${IScalaPlugin().getBundle.getVersion}
            |Scala compiler version:
            |        ${IScalaPlugin().scalaVersion.unparse}
            |Scala library version:
            |        ${platformInstallation.version.unparse}
            |Eclipse version:
            |        ${Platform.getBundle("org.eclipse.platform").getVersion}
            |Class loader store: ${cacheEntries.size} entries
            |        ${entries.mkString("\n\t")}
            |""".stripMargin)

    val group2 = new Group(control, SWT.SHADOW_NONE)
    group2.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false))
    group2.setLayout(new GridLayout(2, false))

    val logFileLink = new Link(group2, SWT.NONE)
    logFileLink.setText("<a>Check</a> the log")
    logFileLink.addListener(SWT.Selection, OpenExternalFile(LogManager.logFile))

    val reportBugLink = new Link(group2, SWT.NONE)
    reportBugLink.setText(s"""and <a href="${SdtConstants.IssueTracker}">report a bug</a>.""")
    reportBugLink.addListener(SWT.Selection, new LinkListener())

    val sveltoLink = new Link(group2, SWT.NONE)
    sveltoLink.setText(s"""Install <a href="${SdtConstants.SveltoHomepage}">svelto</a> to log thread dumps when the UI is unresponsive.""")
    sveltoLink.addListener(SWT.Selection, new LinkListener)
    sveltoLink.setLayoutData({
      val g = new GridData
      g.horizontalSpan = 2
      g
    })

    control
  }

  protected override def createButtonsForButtonBar(parent: Composite) {
    // create only OK button
    createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true)
  }
}
