package org.scalaide.util.eclipse

import org.eclipse.jface.preference._
import org.eclipse.jface.util._
import org.eclipse.jface.viewers._
import org.eclipse.swt.SWT
import org.eclipse.swt.events._
import org.eclipse.swt.layout._
import org.eclipse.swt.widgets._
import org.scalaide.util.ui.DisplayThread
import org.eclipse.ui.PlatformUI
import org.eclipse.swt.widgets.Shell
import org.eclipse.ui.IWorkbenchWindow

// TODO move out implicit conversions to a separate module?
object SWTUtils {

  import scala.language.implicitConversions

  @deprecated("Use org.scalaide.util.ui.DisplayThread.asyncExec", "3.0.0")
  private def asyncExec(f: => Unit) {
    DisplayThread.asyncExec(f)
  }

  @deprecated("Use org.scalaide.util.ui.DisplayThread.syncExec", "3.0.0")
  private def syncExec(f: => Unit) {
    DisplayThread.syncExec(f)
  }

  /** Returns the active workbench window's shell
   *
   *  @return the shell containing this window's controls or `null`
   *   if the shell has not been created yet or if the window has been closed
   */
  def getShell: Shell = getWorkbenchWindow.map(_.getShell).orNull

  /** Returns the currently active window for this workbench (if any). Returns
   *  `null` if there is no active workbench window. Returns
   *  `null` if called from a non-UI thread.
   *
   *  @return the active workbench window, or `null` if there is
   *         no active workbench window or if called from a non-UI thread
   */
  def getWorkbenchWindow: Option[IWorkbenchWindow] = {
    val workbench = PlatformUI.getWorkbench
    Option(workbench.getActiveWorkbenchWindow) orElse workbench.getWorkbenchWindows.headOption
  }

  /** Returns a class that provides implementations for the
   *  methods described by the ModifyListenerListener interface.
   *
   *  @see  [[ org.eclipse.swt.events.MdifyListener ]]
   */
  implicit def fnToModifyListener(f: ModifyEvent => Unit): ModifyListener = new ModifyListener {
    override def modifyText(e: ModifyEvent) = f(e)
  }

  /** Returns an adapter class that provides default implementations for the
   *  methods described by the SelectionListener interface.
   *
   *  @see  [[ org.eclipse.swt.events.SelectionAdapter ]]
   */
  implicit def fnToSelectionAdapter(p: SelectionEvent => Any): SelectionAdapter =
    new SelectionAdapter() {
      override def widgetSelected(e: SelectionEvent) { p(e) }
    }

  implicit def fnToSelectionChangedEvent(p: SelectionChangedEvent => Unit): ISelectionChangedListener = new ISelectionChangedListener() {
    override def selectionChanged(e: SelectionChangedEvent) { p(e) }
  }

  /** A null-arity version of [[ fnToSelectionAdapter ]]
   */
  implicit def noArgFnToSelectionAdapter(p: () => Any): SelectionAdapter =
    new SelectionAdapter() {
      override def widgetSelected(e: SelectionEvent) { p() }
    }

  /** Returns an adapter class that provides default implementations for the
   *  methods described by the MouseListener interface.
   *
   *  @see  [[ org.eclipse.swt.events.MouseAdapter ]]
   */
  implicit def noArgFnToMouseUpListener(f: () => Any): MouseAdapter = new MouseAdapter {
    override def mouseUp(me: MouseEvent) = f()
  }

  /** Returns a class that provides implementations for the
   *  methods described by the IPropertyChangeListener interface.
   *
   *  @see  [[ org.eclipse.swt.events.IPropertyChangeListener ]]
   */
  implicit def fnToPropertyChangeListener(p: PropertyChangeEvent => Any): IPropertyChangeListener =
    new IPropertyChangeListener() {
      override def propertyChange(e: PropertyChangeEvent) { p(e) }
    }

  /** A null-arity version of [[ fnToSelectionChangedEvent ]]
   */
  implicit def noArgFnToSelectionChangedListener(p: () => Any): ISelectionChangedListener =
    new ISelectionChangedListener {
      override def selectionChanged(event: SelectionChangedEvent) { p() }
    }

  /** Returns a class that provides implementations for the
   *  methods described by the IDoubleClickListener interface.
   *
   *  @see  [[ org.eclipse.swt.events.IDoubleClickListener ]]
   */
  implicit def fnToDoubleClickListener(p: DoubleClickEvent => Any): IDoubleClickListener =
    new IDoubleClickListener {
      override def doubleClick(event: DoubleClickEvent) { p(event) }
    }

  implicit def fnToCheckStateListener(p: CheckStateChangedEvent => Unit): ICheckStateListener =
    new ICheckStateListener {
      override def checkStateChanged(event: CheckStateChangedEvent) = p(event)
    }

  /** A class which augments a `Control` with functions to define listeners
   *  for key presses, key releases, and lost focus.
   */
  implicit class RichControl(private val control: Control) extends AnyVal {

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
    def onLabelUpdate(f: AnyRef => String): Unit = column.setLabelProvider(new ColumnLabelProvider() {
        override def getText(elem: AnyRef): String =
          f(elem)
      })
  }

  /** This represents a check box that is associated with a preference, a preference
   *  store and a text label. It is automatically loaded with the preference value
   *  from the store. Furthermore, it automatically saves the preference to the
   *  store when its value changes.
   */
  class CheckBox(store: IPreferenceStore, preference: String, textLabel: String, parent: Composite)
    extends BooleanFieldEditor(preference, textLabel, parent) {

    setPreferenceStore(store)
    load()

    def isChecked: Boolean =
      getBooleanValue()

    def +=(f: SelectionEvent => Unit): Unit =
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
      gd.heightHint = lineHeight * t.getLineHeight()
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

  /** Returns a [[GridData]] configuration, with the given properties.
   *
   *  The possible values for alignment are: [[SWT.BEGINNING]], [[SWT.CENTER]], [[SWT.END]], [[SWT.FILL]]
   */
  def gridData(
    horizontalAlignment: Int = SWT.BEGINNING,
    verticalAlignment: Int = SWT.CENTER,
    grabExcessHorizontalSpace: Boolean = false,
    grabExcessVerticalSpace: Boolean = false,
    horizontalSpan: Int = 1,
    verticalSpan: Int = 1): GridData =
    new GridData(horizontalAlignment, verticalAlignment, grabExcessHorizontalSpace, grabExcessVerticalSpace, horizontalSpan, verticalSpan)

}
