package org.scalaide.ui.internal.preferences

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer
import org.eclipse.jdt.internal.ui.preferences.OverlayPreferenceStore
import org.eclipse.jface.layout.TableColumnLayout
import org.eclipse.jface.preference.PreferencePage
import org.eclipse.jface.text.IDocument
import org.eclipse.jface.viewers.CheckStateChangedEvent
import org.eclipse.jface.viewers.CheckboxTableViewer
import org.eclipse.jface.viewers.ColumnWeightData
import org.eclipse.jface.viewers.IStructuredContentProvider
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.TableViewerColumn
import org.eclipse.jface.viewers.Viewer
import org.eclipse.swt.SWT
import org.eclipse.swt.events.ModifyEvent
import org.eclipse.swt.layout.GridData
import org.eclipse.swt.layout.GridLayout
import org.eclipse.swt.widgets.Composite
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.widgets.Label
import org.eclipse.swt.widgets.Table
import org.eclipse.swt.widgets.Text
import org.eclipse.ui.IWorkbench
import org.eclipse.ui.IWorkbenchPreferencePage
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.internal.extensions.SaveActions
import org.scalaide.extensions.SaveActionSetting
import org.scalaide.util.eclipse.SWTUtils._

/** This class is referenced through plugin.xml */
class SaveActionsPreferencePage extends PreferencePage with IWorkbenchPreferencePage {

  private val MinSaveActionTimeout = 100

  private var textBefore: IDocument = _
  private var textAfter: IDocument = _
  private var descriptionArea: Text = _
  private var timeoutValue: Text = _
  private var viewer: CheckboxTableViewer = _

  private val settings = SaveActions.saveActionSettings.toArray

  private val prefStore = {
    val ps = IScalaPlugin().getPreferenceStore
    import OverlayPreferenceStore._
    val keys = new OverlayKey(STRING, SaveActions.SaveActionTimeoutId) +: settings.map { s =>
      new OverlayKey(BOOLEAN, s.id)
    }
    val store = new OverlayPreferenceStore(ps, keys)
    store.load()
    store
  }

  override def createContents(parent: Composite): Control = {
    val base = new Composite(parent, SWT.NONE)
    base.setLayout(new GridLayout(2, true))

    mkLabel(base, "Save actions are executed for open editors whenever a save event occurs for one of them.", columnSize = 2)

    val timeout = new Composite(base, SWT.NONE)
    timeout.setLayoutData(new GridData(SWT.NONE, SWT.FILL, true, false, 2, 1))
    timeout.setLayout(new GridLayout(2, false))

    timeoutValue = new Text(timeout, SWT.BORDER | SWT.SINGLE)
    timeoutValue.setText(prefStore.getString(SaveActions.SaveActionTimeoutId))
    timeoutValue.addModifyListener { e: ModifyEvent =>
      def error() = {
        setValid(false)
        setErrorMessage(s"Timeout value needs to be >= $MinSaveActionTimeout ms")
      }
      util.Try(timeoutValue.getText().toInt) match {
        case util.Success(e) =>
          if (e >= MinSaveActionTimeout) {
            setValid(true)
            setErrorMessage(null)
          }
          else error
        case util.Failure(_) =>
          error
      }
    }
    mkLabel(timeout, "Timout in milliseconds (this is the time the IDE waits for a result of the save action)")

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
      table.getSelection().headOption foreach { item =>
        selectSaveAction(item.getData().asInstanceOf[SaveActionSetting])
      }
    }
    viewer.addCheckStateListener { e: CheckStateChangedEvent =>
      prefStore.setValue(e.getElement.asInstanceOf[SaveActionSetting].id, e.getChecked)
    }

    val columnEnabled = new TableViewerColumn(viewer, SWT.NONE)
    columnEnabled.getColumn().setText("Name")
    columnEnabled onLabelUpdate { _.asInstanceOf[SaveActionSetting].name }
    tcl.setColumnData(columnEnabled.getColumn(), new ColumnWeightData(1, true))

    viewer.setInput(settings.sortBy(_.name))
    viewer.setAllChecked(false)
    viewer.setCheckedElements(settings.filter(isEnabled).asInstanceOf[Array[AnyRef]])

    mkLabel(base, "Description:", columnSize = 2)

    descriptionArea = mkTextArea(base, lineHeight = 3, initialText = "", columnSize = 2)

    mkLabel(base, "Before:")
    mkLabel(base, "After:")

    val previewTextBefore = createPreviewer(base) {
      textBefore = _
    }
    previewTextBefore.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))

    val previewTextAfter = createPreviewer(base) {
      textAfter = _
    }
    previewTextAfter.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true))

    base
  }

  override def init(workbench: IWorkbench): Unit = ()

  override def performOk(): Boolean = {
    prefStore.setValue(SaveActions.SaveActionTimeoutId, timeoutValue.getText())
    prefStore.propagate()
    super.performOk()
  }

  override def performDefaults(): Unit = {
    timeoutValue.setText(SaveActionsPreferenceInitializer.SaveActionDefaultTimeout.toString())
    viewer.setAllChecked(false)
    prefStore.loadDefaults()
    super.performDefaults
  }

  private def mkTextArea(parent: Composite, lineHeight: Int, initialText: String, columnSize: Int): Text = {
    val t = new Text(parent, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.WRAP | SWT.READ_ONLY)
    t.setText(initialText)
    t.setLayoutData({
      val gd = new GridData(SWT.FILL, SWT.FILL, true, false, columnSize, 1)
      gd.heightHint = lineHeight*t.getLineHeight()
      gd
    })
    t
  }

  private def isEnabled(saveAction: SaveActionSetting): Boolean =
    prefStore.getBoolean(saveAction.id)

  private def selectSaveAction(saveAction: SaveActionSetting) = {
    textBefore.set(saveAction.codeExample)
    textAfter.set("Previewing the behavior of the save action is not yet implemented.")
    descriptionArea.setText(saveAction.description)
  }

  private def createPreviewer(parent: Composite)(f: IDocument => Unit): Control = {
    val previewer = new PreviewerFactory(ScalaPreviewerFactoryConfiguration).createPreviewer(parent, IScalaPlugin().getPreferenceStore, "")
    f(previewer.getDocument())
    previewer.getControl
  }

  private object ContentProvider extends IStructuredContentProvider {

    override def dispose(): Unit = ()

    override def getElements(input: Any): Array[AnyRef] = {
      input.asInstanceOf[Array[AnyRef]]
    }

    override def inputChanged(viewer: Viewer, oldInput: Any, newInput: Any): Unit = ()
  }
}

object SaveActionsPreferenceInitializer {
  /** Default timeout value in milliseconds */
  final val SaveActionDefaultTimeout: Int = 200
}

/** This class is referenced through plugin.xml */
class SaveActionsPreferenceInitializer extends AbstractPreferenceInitializer {
  import SaveActionsPreferenceInitializer._

  override def initializeDefaultPreferences(): Unit = {
    SaveActions.saveActionSettings foreach { s =>
      IScalaPlugin().getPreferenceStore().setDefault(s.id, false)
    }
    IScalaPlugin().getPreferenceStore().setDefault(SaveActions.SaveActionTimeoutId, SaveActionDefaultTimeout)
  }
}
