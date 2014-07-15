package org.scalaide.ui.internal.preferences

import org.eclipse.core.runtime.Path
import org.eclipse.jface.preference.PreferencePage
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.jface.viewers.IStructuredSelection
import org.eclipse.jface.viewers.ListViewer
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.swt.SWT
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.layout.FillLayout
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Button
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.DirectoryDialog
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.scalaide.core.internal.project.LabeledDirectoryScalaInstallation
import org.scalaide.core.internal.project.DirectoryScalaInstallation.directoryScalaInstallationFactory
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.ui.internal.project.ScalaInstallationUIProviders
import scala.collection.JavaConverters.asScalaIteratorConverter
import org.eclipse.jface.dialogs.InputDialog
import org.eclipse.jface.dialogs.IInputValidator
import org.eclipse.jface.window.Window
import org.scalaide.core.internal.project.LabeledDirectoryScalaInstallation
import org.eclipse.core.runtime.IStatus
import org.eclipse.debug.core.DebugPlugin
import org.eclipse.core.runtime.Status
import org.scalaide.util.internal.eclipse.FileUtils
import org.scalaide.core.ScalaPlugin
import org.scalaide.core.internal.project.ModifiedScalaInstallations
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import scala.collection.mutable.Publisher
import org.scalaide.core.internal.project.CustomScalaInstallationLabel
import org.scalaide.core.internal.project.LabeledScalaInstallation
import scala.PartialFunction.cond

class InstalledScalasPreferencePage extends PreferencePage with IWorkbenchPreferencePage with ScalaInstallationUIProviders with Publisher[ModifiedScalaInstallations] {

  def itemTitle = "Scala"
  var customInstallations = ScalaInstallation.customInstallations
  subscribe(ScalaInstallation.installationsTracker)
  noDefaultAndApplyButton()

  override def performOk(): Boolean = {
    ScalaInstallation.customInstallations &~ customInstallations foreach {ScalaInstallation.customInstallations.remove(_)}
    customInstallations &~ ScalaInstallation.customInstallations foreach {ScalaInstallation.customInstallations.add(_)}
    publish(ModifiedScalaInstallations())
    super.performOk()
  }

  def createContents(parent: Composite): Control = {
    import org.scalaide.util.internal.eclipse.SWTUtils._
    val composite = new Composite(parent, SWT.NONE)

    composite.setLayout(new GridLayout(2, false))

    val list = new ListViewer(composite)
    list.getControl().setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))
    list.setContentProvider(new ContentProvider())
    val installationLabels = new LabelProvider
    list.setLabelProvider(installationLabels)
    list.setInput(ScalaInstallation.availableInstallations)

    val buttons = new Composite(composite, SWT.NONE)
    buttons.setLayoutData(new GridData(SWT.BEGINNING, SWT.TOP, false, true))
    buttons.setLayout(new FillLayout(SWT.VERTICAL))

    val buttonAdd = new Button(composite, SWT.PUSH)
    buttonAdd.setText("Add")
    buttonAdd.setEnabled(true)

    buttonAdd.addSelectionListener({ (e: SelectionEvent) =>
      import org.scalaide.ui.internal.handlers.{ GenericExceptionStatusHandler => GS }
      val shell = parent.getShell()
      val dirDialog = new DirectoryDialog(shell)
      dirDialog.setText("Select your scala directory")
      val selectedDir = dirDialog.open()
      if (selectedDir != null) {
        def genericExceptionStatus(e: IllegalArgumentException) = new Status(IStatus.ERROR, ScalaPlugin.plugin.pluginId, GS.STATUS_CODE_EXCEPTION, "", e)
        def manageStatus(status: IStatus) = {
          val handler = DebugPlugin.getDefault().getStatusHandler(status)
          handler.handleStatus(status, this)
        }

        val dir = new Path(selectedDir)
        if (!dir.toFile().isDirectory()) {
          val errorStatus = genericExceptionStatus(new IllegalArgumentException("This selection is not a valid directory !"))
          manageStatus(errorStatus)
        } else {
          directoryScalaInstallationFactory(dir) match {
            case Failure(thrown) => thrown match {
              case e: IllegalArgumentException => manageStatus(genericExceptionStatus(e))
              case _ => throw (thrown)
            }
            case Success(si) =>
              // give a label to this DirectoryScalaInstallation
              val dlg = new InputDialog(shell, "", "Enter a name for this Scala Installation", "", new IInputValidator() {
                override def isValid(newText: String): String = {
                  if (labels contains newText) "This is a reserved name."
                  else if (customInstallations.flatMap(_.getName()) contains newText) "This name is already used by a custom Scala installation."
                  else null
                }
              })

              if (dlg.open() == Window.OK) {
                // User clicked OK; update the label with the input
                val lsi = new LabeledDirectoryScalaInstallation(dlg.getValue(), si)
                customInstallations += lsi
                list.add(lsi)
              }
          }
        }
      }
    })

    val buttonRemove = new Button(composite, SWT.PUSH)
    buttonRemove.setText("Remove")
    buttonRemove.setEnabled(false)

    buttonRemove.addSelectionListener({ (e: SelectionEvent) =>
      val selection = list.getSelection().asInstanceOf[IStructuredSelection]
      selection.iterator().asScala foreach { s =>
        s match {
          case d: LabeledScalaInstallation if cond(d.label) { case CustomScalaInstallationLabel(tag) => true } =>
            customInstallations -= d
            list.remove(d)
          case _ => ()
        }
      }
    })

    list.addSelectionChangedListener({ (event: SelectionChangedEvent) =>
      val selection = event.getSelection()
      if (selection.isEmpty()) buttonRemove.setEnabled(false) else buttonRemove.setEnabled(true)
    })

    composite
  }

  def init(workbench: IWorkbench): Unit = {}

}