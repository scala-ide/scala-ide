package org.scalaide.sbt.ui.events

import org.eclipse.swt.events.SelectionEvent
import org.eclipse.swt.events.SelectionAdapter
import org.eclipse.swt.events.FocusEvent
import org.eclipse.swt.events.FocusAdapter

object EventListenerAdapters {
  def onWidgetSelected(f: SelectionEvent => Unit): SelectionAdapter = new SelectionAdapter() {
    override def widgetSelected(e: SelectionEvent): Unit = f(e)
  }
  
  def onFocusLost(f: FocusEvent => Unit): FocusAdapter = new FocusAdapter() {
    override def focusLost(e: FocusEvent): Unit = f(e)
  }
}