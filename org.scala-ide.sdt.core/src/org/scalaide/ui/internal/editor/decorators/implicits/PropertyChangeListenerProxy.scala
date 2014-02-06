package org.scalaide.ui.internal.editor.decorators.implicits

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import java.lang.ref.WeakReference

object PropertyChangeListenerProxy {

  def apply(listener: IPropertyChangeListener, stores: IPreferenceStore*) =
    new PropertyChangeListenerProxy(new WeakReference(listener), stores: _*)

}

class PropertyChangeListenerProxy(listenerRef: WeakReference[IPropertyChangeListener],  stores: IPreferenceStore*)
    extends IPropertyChangeListener {

  def propertyChange(event: PropertyChangeEvent) {
    Option(listenerRef.get) match {
      case None =>
        stores.foreach { _.removePropertyChangeListener(this) }
      case Some(listener) =>
        listener.propertyChange(event)
    }
  }

  def autoRegister() = {
    stores.foreach { _.addPropertyChangeListener(this) }
  }
}
