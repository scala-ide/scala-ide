package scala.tools.eclipse.util

import org.eclipse.swt.widgets.Display
import org.eclipse.jface.viewers.DoubleClickEvent
import org.eclipse.jface.viewers.IDoubleClickListener
import org.eclipse.jface.viewers.SelectionChangedEvent
import org.eclipse.jface.viewers.ISelectionChangedListener
import org.eclipse.swt.widgets.Control
import org.eclipse.swt.events.KeyEvent
import org.eclipse.swt.events.KeyAdapter
import org.eclipse.swt.events.FocusAdapter
import org.eclipse.swt.events.FocusEvent
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.swt.events._
import scala.tools.eclipse.ui.DisplayThread

// TODO move out implicit conversions to a separate module?
object SWTUtils {

  import scala.language.implicitConversions

  @deprecated("Use scala.tools.eclipse.ui.DisplayThread.asyncExec", "3.0.0")
  def asyncExec(f: => Unit) {
    DisplayThread.asyncExec(f)
  }

  @deprecated("Use scala.tools.eclipse.ui.DisplayThread.syncExec", "3.0.0")
  def syncExec(f: => Unit) {
    DisplayThread.syncExec(f)
  }

  implicit def fnToModifyListener(f: ModifyEvent => Unit): ModifyListener = new ModifyListener {
    def modifyText(e: ModifyEvent) = f(e)
  }

  // TODO this method is unused. remove?
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

  implicit class PimpedControl(control: Control) {

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