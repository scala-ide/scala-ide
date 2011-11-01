package scala.tools.eclipse
package semantic.highlighting

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent

class PropertyChangeListenerProxy(l : IPropertyChangeListener, private val stores : IPreferenceStore*) extends IPropertyChangeListener {
  import java.lang.ref.WeakReference
  val ref = new WeakReference(l)
  
  def propertyChange(event: PropertyChangeEvent) {
    ref.get match {
      case null => {
        stores.foreach{ _.removePropertyChangeListener(this) }
      }
      case o => o.propertyChange(event)
    }
  }
  
  def autoRegister() = {
    stores.foreach{ _.addPropertyChangeListener(this) }
  }
}
