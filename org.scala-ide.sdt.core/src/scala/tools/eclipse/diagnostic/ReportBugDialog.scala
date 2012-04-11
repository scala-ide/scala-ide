package scala.tools.eclipse

package diagnostic

import org.eclipse.jface.dialogs.{ Dialog, IDialogConstants }
import org.eclipse.swt.widgets.{ List => SWTList, _ }
import org.eclipse.swt.layout.{ RowLayout, GridLayout, GridData }
import org.eclipse.ui.internal.layout.CellLayout
import org.eclipse.swt.SWT
import org.eclipse.swt.events.{ ModifyListener, ModifyEvent, SelectionAdapter, SelectionListener, SelectionEvent }
import org.eclipse.core.runtime.Platform

import scala.tools.eclipse.logging.LogManager
import scala.tools.eclipse.ui.OpenExternalFile


class ReportBugDialog(shell: Shell) extends Dialog(shell) {
  
  val SDT_TRACKER_URL = "https://www.assembla.com/spaces/scala-ide/support/tickets"
  
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
        "Scala plugin version: " + ScalaPlugin.plugin.getBundle.getVersion + "\n\n" +
        "Scala compiler version:\t" + ScalaPlugin.plugin.scalaCompilerBundleVersion + "\n" +
        "Scala library version:\t" + ScalaPlugin.plugin.scalaLibBundle.getVersion + "\n" +
        "Eclipse version: " + Platform.getBundle("org.eclipse.platform").getVersion)    

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
    reportBugLink.setText("and <a href=\"" + SDT_TRACKER_URL + "\">report a bug</a>.")      
    reportBugLink.addListener(SWT.Selection, DiagnosticDialog.linkListener)
    
    control
  }
  
  protected override def createButtonsForButtonBar(parent: Composite) {
    // create only OK button
    createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true)
  }  
}