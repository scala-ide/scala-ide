package scala.tools.eclipse.util

import org.eclipse.swt.widgets.Display
import org.eclipse.jface.viewers.{ DoubleClickEvent, IDoubleClickListener, SelectionChangedEvent, ISelectionChangedListener }
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.events.{ KeyEvent, KeyAdapter, FocusAdapter, FocusEvent }
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.swt.events._

object SWTUtils {
  
  /** Asynchronously run `f` on the UI thread.  */
  def asyncExec(f: => Unit) {
    Display.getDefault asyncExec new Runnable {
      override def run() { f }
    }
  }

  /** Synchronously run `f` on the UI thread.  */
  def syncExec(f: => Unit) {
    Display.getDefault syncExec new Runnable {
      override def run() { f }
    }
  }
  
  implicit def fnToModifyListener(f: ModifyEvent => Unit): ModifyListener = new ModifyListener {
    def modifyText(e: ModifyEvent) = f(e)
  }

  implicit def fnToValListener(f: VerifyEvent => Unit) = new VerifyListener {
    def verifyText(e: VerifyEvent) = f(e)
  }

  implicit def fnToSelectionAdapter(p: SelectionEvent => Any): SelectionAdapter =
    new SelectionAdapter() {
      override def widgetSelected(e: SelectionEvent) { p(e) }
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
      def propertyChange(e: PropertyChangeEvent) { p(e) }
    }

  implicit def noArgFnToSelectionChangedListener(p: () => Any): ISelectionChangedListener =
    new ISelectionChangedListener {
      def selectionChanged(event: SelectionChangedEvent) { p() }
    }

  implicit def fnToDoubleClickListener(p: DoubleClickEvent => Any): IDoubleClickListener =
    new IDoubleClickListener {
      def doubleClick(event: DoubleClickEvent) { p(event) }
    }

  implicit def control2PimpedControl(control: Control): PimpedControl = new PimpedControl(control)

  class PimpedControl(control: Control) {

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

}