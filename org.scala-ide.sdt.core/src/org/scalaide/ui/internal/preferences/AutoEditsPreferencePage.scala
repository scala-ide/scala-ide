package org.scalaide.ui.internal.preferences

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jdt.internal.ui.preferences.OverlayPreferenceStore
import org.eclipse.jface.layout.TableColumnLayout
import org.eclipse.jface.preference.PreferencePage
import org.eclipse.jface.viewers.CheckStateChangedEvent
import org.eclipse.jface.viewers.CheckboxTableViewer
import org.eclipse.jface.viewers.ColumnWeightData
import org.eclipse.jface.viewers.IStructuredContentProvider
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.StructuredSelection
import org.eclipse.jface.viewers.TableViewerColumn
import org.eclipse.jface.viewers.Viewer
import org.eclipse.swt.SWT
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Table
import org.eclipse.swt.widgets.Text
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.internal.extensions.AutoEdits
import org.scalaide.extensions.AutoEditSetting
import org.scalaide.util.internal.Commons
import org.scalaide.util.eclipse.SWTUtils._

/** This class is referenced through plugin.xml */
class AutoEditsPreferencePage extends PreferencePage with IWorkbenchPreferencePage {

  private var descriptionArea: Text = _
  private var configurationArea: Text = _
  private var viewer: CheckboxTableViewer = _

  private val settings = AutoEdits.autoEditSettings.toArray

  private val prefStore = {
    val ps = IScalaPlugin().getPreferenceStore
    val keys = settings flatMap { s ⇒
      import OverlayPreferenceStore._
      Seq(
        new OverlayKey(BOOLEAN, s.id),
        new OverlayKey(STRING, s.configId)
      )
    }
    val store = new OverlayPreferenceStore(ps, keys.toArray)
    store.load()
    store
  }

  override def createContents(parent: Composite): Control = {
    val base = new Composite(parent, SWT.NONE)
    base.setLayout(new GridLayout(2, true))

    mkLabel(base, "Auto edits are executed whenever you type to support you in editing your code.", columnSize = 2)

    val tableComposite = new Composite(base, SWT.NONE)
    tableComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1))

    val table = new Table(tableComposite, SWT.CHECK | SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION | SWT.V_SCROLL)
    table.setHeaderVisible(true)
    table.setLinesVisible(true)

    val tcl = new TableColumnLayout
    tableComposite.setLayout(tcl)

    viewer = new CheckboxTableViewer(table)
    viewer.setContentProvider(ContentProvider)
    viewer.addSelectionChangedListener { e: SelectionChangedEvent =>
      table.getSelection.headOption foreach { item ⇒
        selectAutoEdit(item.getData().asInstanceOf[AutoEditSetting])
      }
    }
    viewer.addCheckStateListener { e: CheckStateChangedEvent =>
      prefStore.setValue(e.getElement.asInstanceOf[AutoEditSetting].id, e.getChecked)
    }

    val columnEnabled = new TableViewerColumn(viewer, SWT.NONE)
    columnEnabled.getColumn().setText("Name")
    columnEnabled onLabelUpdate { _.asInstanceOf[AutoEditSetting].name }
    tcl.setColumnData(columnEnabled.getColumn(), new ColumnWeightData(1, true))

    viewer.setInput(settings.sortBy(_.name))
    viewer.setAllChecked(false)
    viewer.setCheckedElements(settings.filter(isEnabled).asInstanceOf[Array[AnyRef]])

    mkLabel(base, "Configuration:", columnSize = 2)
    configurationArea = mkTextArea(base, lineHeight = 3, columnSize = 2)
    configurationArea.addModifyListener { e: ModifyEvent ⇒

      /*
       * Checks if the following properties are hold:
       * - each line contains one key-value pair
       * - a key-value pair is separated by a '=' sign
       * - no value needs to be specified, in this case the value is treated as
       *   the value 'true'
       */
      def isValid(str: String): Boolean = {
        str.isEmpty() || Commons.split(str, '\n').forall { line ⇒
          Commons.split(line, '=') match {
            case Seq(k, _) if k.nonEmpty ⇒ true
            case Seq(k) if k.nonEmpty ⇒ true
            case _ ⇒ false
          }
        }
      }

      val text = configurationArea.getText()
      if (!isValid(text)) {
        setValid(false)
        setErrorMessage("Invalid configuration")
      }
      else {
        setValid(true)
        setErrorMessage(null)

        viewer.getSelection() match {
          case s: StructuredSelection ⇒
            Option(s.getFirstElement()).map(_.asInstanceOf[AutoEditSetting]) foreach { aes ⇒
              prefStore.setValue(aes.configId, text)
            }
        }
      }
    }

    mkLabel(base, "Description:", columnSize = 2)
    descriptionArea = mkTextArea(base, lineHeight = 6, columnSize = 2, style = SWT.READ_ONLY)

    base
  }

  override def init(workbench: IWorkbench): Unit = ()

  override def performOk(): Boolean = {
    prefStore.propagate()
    super.performOk()
  }

  override def performDefaults(): Unit = {
    viewer.setAllChecked(false)
    prefStore.loadDefaults()
    super.performDefaults
  }

  private def autoEditConfig(autoEdit: AutoEditSetting): String =
    prefStore.getString(autoEdit.configId)

  private def isEnabled(autoEdit: AutoEditSetting): Boolean =
    prefStore.getBoolean(autoEdit.id)

  private def selectAutoEdit(autoEdit: AutoEditSetting) = {
    descriptionArea.setText(autoEdit.description)
    configurationArea.setText(autoEditConfig(autoEdit))
  }

  private object ContentProvider extends IStructuredContentProvider {

    override def dispose(): Unit = ()

    override def getElements(input: Any): Array[AnyRef] = {
      input.asInstanceOf[Array[AnyRef]]
    }

    override def inputChanged(viewer: Viewer, oldInput: Any, newInput: Any): Unit = ()
  }
}

/** This class is referenced through plugin.xml */
class AutoEditsPreferenceInitializer extends AbstractPreferenceInitializer {

  override def initializeDefaultPreferences(): Unit = {
    AutoEdits.autoEditSettings foreach { s =>
      IScalaPlugin().getPreferenceStore().setDefault(s.id, false)
    }
  }
}
