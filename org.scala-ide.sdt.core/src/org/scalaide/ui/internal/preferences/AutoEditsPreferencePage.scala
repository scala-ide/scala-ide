package org.scalaide.ui.internal.preferences

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jface.layout.TableColumnLayout
import org.eclipse.jface.preference.PreferencePage
import org.eclipse.jface.viewers.CheckStateChangedEvent
import org.eclipse.jface.viewers.CheckboxTableViewer
import org.eclipse.jface.viewers.ColumnWeightData
import org.eclipse.jface.viewers.IStructuredContentProvider
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.TableViewerColumn
import org.eclipse.jface.viewers.Viewer
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Table
import org.eclipse.swt.widgets.Text
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.scalaide.core.ScalaPlugin
import org.scalaide.extensions.AutoEditSetting
import org.scalaide.ui.internal.editor.AutoEditExtensions
import org.scalaide.util.internal.eclipse.SWTUtils._

/** This class is referenced through plugin.xml */
class AutoEditsPreferencePage extends PreferencePage with IWorkbenchPreferencePage {

  private val prefStore = ScalaPlugin.prefStore

  private var descriptionArea: Text = _
  private var viewer: CheckboxTableViewer = _

  private val settings = AutoEditExtensions.autoEditSettings.toArray

  private var changes = Set[AutoEditSetting]()

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
      selectAutoEdit(table.getSelection().head.getData().asInstanceOf[AutoEditSetting])
    }
    viewer.addCheckStateListener { e: CheckStateChangedEvent =>
      toggleAutoEdit(e.getElement().asInstanceOf[AutoEditSetting])
    }

    val columnEnabled = new TableViewerColumn(viewer, SWT.NONE)
    columnEnabled.getColumn().setText("Name")
    columnEnabled onLabelUpdate { _.asInstanceOf[AutoEditSetting].name }
    tcl.setColumnData(columnEnabled.getColumn(), new ColumnWeightData(1, true))

    viewer.setInput(settings.sortBy(_.name))
    viewer.setAllChecked(false)
    viewer.setCheckedElements(settings.filter(isEnabled).asInstanceOf[Array[AnyRef]])

    mkLabel(base, "Description:", columnSize = 2)

    descriptionArea = mkTextArea(base, lineHeight = 3, columnSize = 2)

    base
  }

  override def init(workbench: IWorkbench): Unit = ()

  override def performOk(): Boolean = {
    changes foreach { saveAction =>
      val previousValue = prefStore.getBoolean(saveAction.id)
      prefStore.setValue(saveAction.id, !previousValue)
    }
    super.performOk()
  }

  override def performDefaults(): Unit = {
    viewer.setAllChecked(false)
    changes = Set()
    settings foreach (changes += _)
    super.performDefaults
  }

  private def isEnabled(autoEdit: AutoEditSetting): Boolean =
    prefStore.getBoolean(autoEdit.id)

  private def toggleAutoEdit(autoEdit: AutoEditSetting) = {
    if (changes.contains(autoEdit))
      changes -= autoEdit
    else
      changes += autoEdit
  }

  private def selectAutoEdit(autoEdit: AutoEditSetting) = {
    descriptionArea.setText(autoEdit.description)
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
    AutoEditExtensions.autoEditSettings foreach { s =>
      ScalaPlugin.prefStore.setDefault(s.id, false)
    }
  }
}
