package scala.tools.eclipse
package semantic.highlighting

import scala.tools.eclipse.properties.ImplicitsPreferencePage

import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.ui.commands.ICommandService
import org.eclipse.ui.commands.IElementUpdater
import org.eclipse.ui.menus.UIElement
import org.eclipse.ui.PlatformUI

/**
 * Handler to toggle the Implicits Display (shortcut to avoid open Preferences,...)
 * 
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */

class ToggleImplicitsDisplayHandler extends AbstractHandler with IElementUpdater {
  
  private val _commandId = "org.scala-ide.sdt.core.commands.ToggleImplicitsDisplay"

  /**
   * @throws ExecutionException
   */
  def execute(event : ExecutionEvent) : Object = {
    // see http://eclipsesource.com/blogs/2009/01/15/toggling-a-command-contribution/
    // see https://bugs.eclipse.org/bugs/show_bug.cgi?id=154130#c27
    // see http://wiki.eclipse.org/Menu_Contributions
    toggle()
    // refresh() // refresh will be triggered property update
    null
  }

  /**
   * refresh widget linked to this handlers (button, menu items,...)
   */
  def updateElement(element : UIElement, paramters : java.util.Map[_,_]) {
    element.setChecked(isChecked)    
  }

  private def isChecked  : Boolean = {
    pluginStore.getBoolean(ImplicitsPreferencePage.P_ACTIVE)
  }  
  
  private def toggle() : Boolean = {
    val newValue = !pluginStore.getBoolean(ImplicitsPreferencePage.P_ACTIVE)
    pluginStore.setValue(ImplicitsPreferencePage.P_ACTIVE, newValue.toString)
    newValue
  }  

  private def refresh() {
    val service = PlatformUI.getWorkbench().getService(classOf[ICommandService]).asInstanceOf[ICommandService]
    service.refreshElements(_commandId, null)

  }

  // listen change on the property regardless the source of the change (preferences page, widget linked to the handler)
  private val _listener = new IPropertyChangeListener {
    def propertyChange(event: PropertyChangeEvent) {
      if (event.getProperty() == ImplicitsPreferencePage.P_ACTIVE) {
        refresh()
      }
    }
  }
  
  protected def pluginStore : IPreferenceStore = ScalaPlugin.plugin.getPreferenceStore

  new PropertyChangeListenerProxy(_listener, pluginStore).autoRegister()
}
