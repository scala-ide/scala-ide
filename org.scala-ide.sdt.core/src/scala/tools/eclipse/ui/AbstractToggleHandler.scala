package scala.tools.eclipse.ui

import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.ui.commands.ICommandService
import org.eclipse.ui.commands.IElementUpdater
import org.eclipse.ui.PlatformUI
import org.eclipse.jface.util.IPropertyChangeListener
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.ui.menus.UIElement
import org.eclipse.jface.util.PropertyChangeEvent
import scala.tools.eclipse.semantichighlighting.implicits.PropertyChangeListenerProxy
import org.eclipse.core.commands.AbstractHandler

/** Base handler for a toggle command linked to a platform preference.
 *
 *  The preference is updated when button is pushed. The UI element is updated when the value of the preference is modified.
 */
abstract class AbstractToggleHandler(commandId: String, preferenceId: String) extends AbstractHandler with IElementUpdater {

 private def pluginStore: IPreferenceStore = ScalaPlugin.plugin.getPreferenceStore
 
  /** Call when the button is push.
   */
  def execute(event: ExecutionEvent): Object = {
    // see http://eclipsesource.com/blogs/2009/01/15/toggling-a-command-contribution/
    // see https://bugs.eclipse.org/bugs/show_bug.cgi?id=154130#c27
    // see http://wiki.eclipse.org/Menu_Contributions
    toggle()
    null
  }

  /** Update the UI element state according to the preference.
   */
  def updateElement(element: UIElement, parameters: java.util.Map[_, _]) {
    element.setChecked(isChecked)
  }

  private def isChecked: Boolean = {
    pluginStore.getBoolean(preferenceId)
  }

  private def toggle(): Boolean = {
    val newValue = !pluginStore.getBoolean(preferenceId)
    pluginStore.setValue(preferenceId, newValue.toString)
    newValue
  }

  PropertyChangeListenerProxy(_listener, pluginStore).autoRegister()
  
  // listen change on the property regardless the source of the change (preferences page, widget linked to the handler)
  private val _listener = new IPropertyChangeListener {
    def propertyChange(event: PropertyChangeEvent) {
      if (event.getProperty() == preferenceId) {
        refresh()
      }
    }
  }

  private def refresh() {
    val service = PlatformUI.getWorkbench().getService(classOf[ICommandService]).asInstanceOf[ICommandService]
    service.refreshElements(commandId, null)
  }
}
