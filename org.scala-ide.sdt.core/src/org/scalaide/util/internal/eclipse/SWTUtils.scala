package org.scalaide.util.internal.eclipse

import org.eclipse.jface.preference._
import org.eclipse.jface.util._
import org.eclipse.jface.viewers._
import org.eclipse.swt.events._
import org.eclipse.swt.widgets._
import org.scalaide.util.internal.ui.DisplayThread
import org.eclipse.ui.PlatformUI
import org.eclipse.swt.widgets.Shell
import org.eclipse.ui.IWorkbenchWindow
import org.eclipse.swt.SWT
import org.eclipse.swt.layout.GridData

// TODO move out implicit conversions to a separate module?
object SWTUtils {

  import scala.language.implicitConversions

  @deprecated("Use org.scalaide.util.internal.ui.DisplayThread.asyncExec", "3.0.0")
  def asyncExec(f: => Unit) {
    DisplayThread.asyncExec(f)
  }

  @deprecated("Use org.scalaide.util.internal.ui.DisplayThread.syncExec", "3.0.0")
  def syncExec(f: => Unit) {
    DisplayThread.syncExec(f)
  }

  def getShell: Shell = getWorkbenchWindow.map(_.getShell).orNull

  def getWorkbenchWindow: Option[IWorkbenchWindow] = {
    val workbench = PlatformUI.getWorkbench
    Option(workbench.getActiveWorkbenchWindow) orElse workbench.getWorkbenchWindows.headOption
  }

  implicit def fnToModifyListener(f: ModifyEvent => Unit): ModifyListener = new ModifyListener {
    override def modifyText(e: ModifyEvent) = f(e)
  }

  implicit def fnToSelectionAdapter(p: SelectionEvent => Any): SelectionAdapter =
    new SelectionAdapter() {
      override def widgetSelected(e: SelectionEvent) { p(e) }
    }

  implicit def fnToSelectionChangedEvent(p:SelectionChangedEvent => Unit): ISelectionChangedListener = new ISelectionChangedListener() {
    override def selectionChanged(e: SelectionChangedEvent) { p(e) }
  }

  implicit def noArgFnToSelectionAdapter(p: () => Any): SelectionAdapter =
    new SelectionAdapter() {
      override def widgetSelected(e: SelectionEvent) { p() }
    }

  implicit def noArgFnToMouseUpListener(f: () => Any): MouseAdapter = new MouseAdapter {
    override def mouseUp(me: MouseEvent) = f()
  }

  implicit def fnToPropertyChangeListener(p: PropertyChangeEvent => Any): IPropertyChangeListener =
    new IPropertyChangeListener() {
      override def propertyChange(e: PropertyChangeEvent) { p(e) }
    }

  implicit def noArgFnToSelectionChangedListener(p: () => Any): ISelectionChangedListener =
    new ISelectionChangedListener {
      override def selectionChanged(event: SelectionChangedEvent) { p() }
    }

  implicit def fnToDoubleClickListener(p: DoubleClickEvent => Any): IDoubleClickListener =
    new IDoubleClickListener {
      override def doubleClick(event: DoubleClickEvent) { p(event) }
    }

  implicit def fnToCheckStateListener(p: CheckStateChangedEvent => Unit): ICheckStateListener =
    new ICheckStateListener {
      override def checkStateChanged(event: CheckStateChangedEvent) = p(event)
    }

  implicit class PimpedControl(private val control: Control) extends AnyVal {

    def onKeyReleased(p: KeyEvent => Any) {
      control.addKeyListener(new KeyAdapter {
        override def keyReleased(e: KeyEvent) { p(e) }
      })
    }

    def onKeyReleased(p: => Any) {
      control.addKeyListener(new KeyAdapter {
        override def keyReleased(e: KeyEvent) { p }
      })
    }

    def onFocusLost(p: => Any) {
      control.addFocusListener(new FocusAdapter {
        override def focusLost(e: FocusEvent) { p }
      })
    }

  }

  implicit class RichTableViewerColumn(private val column: TableViewerColumn) extends AnyVal {
    def onLabelUpdate(f: AnyRef => String) =
      column.setLabelProvider(new ColumnLabelProvider() {
        override def getText(elem: AnyRef): String =
          f(elem)
      })
  }

  /**
   * This represents a check box that is associated with a preference, a preference
   * store and a text label. It is automatically loaded with the preference value
   * from the store. Furthermore, it automatically saves the preference to the
   * store when its value changes.
   */
  class CheckBox(store: IPreferenceStore, preference: String, textLabel: String, parent: Composite)
      extends BooleanFieldEditor(preference, textLabel, parent) {

    setPreferenceStore(store)
    load()

    def isChecked: Boolean =
      getBooleanValue()

    def += (f: SelectionEvent => Unit): Unit =
      getChangeControl(parent) addSelectionListener { (e: SelectionEvent) => f(e) }
  }

  /**
   * Creates a multi line text area, whose layout data interops with the grid
   * layout.
   */
  def mkTextArea(parent: Composite, lineHeight: Int = 1, initialText: String = "", columnSize: Int = 1, style: Int = 0): Text = {
    val t = new Text(parent, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.WRAP | style)
    t.setText(initialText)
    t.setLayoutData({
      val gd = new GridData(SWT.FILL, SWT.FILL, true, false, columnSize, 1)
      gd.heightHint = lineHeight*t.getLineHeight()
      gd
    })
    t
  }

  /**
   * Creates a label, whose layout data interops with the grid layout.
   */
  def mkLabel(parent: Composite, text: String, columnSize: Int = 1): Label = {
    val lb = new Label(parent, SWT.NONE)
    lb.setText(text)
    lb.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false, columnSize, 1))
    lb
  }
}
