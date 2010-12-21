package scala.tools.eclipse
package ui.preferences

import scala.tools.eclipse.internal.logging.Tracer
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent

class PropertyChangeListenerProxy(l : IPropertyChangeListener, val stores : IPreferenceStore*) extends IPropertyChangeListener {
  import java.lang.ref.WeakReference
  val ref = new WeakReference(l)
  
  def propertyChange(event: PropertyChangeEvent) {
    Tracer.println("propertyChange : " + event)
    ref.get match {
      case null => {
        Tracer.println("removePropertyChangeListener")
        stores.foreach{ _.removePropertyChangeListener(this) }
      }
      case o => o.propertyChange(event)
    }
  }
  
  def autoRegister() = {
    Tracer.println("autoRegisterPropertyChangeListener")
    stores.foreach{ _.addPropertyChangeListener(this) }
  }
}