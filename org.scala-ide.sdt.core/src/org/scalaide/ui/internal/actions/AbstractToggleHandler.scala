/*
 * Copyright (c) 2014 Contributor. All rights reserved.
 */
package org.scalaide.ui.internal.actions

import org.eclipse.core.commands.AbstractHandler
import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.ui.PlatformUI
import org.eclipse.ui.commands.ICommandService
import org.eclipse.ui.commands.IElementUpdater
import org.eclipse.ui.menus.UIElement
import org.scalaide.core.IScalaPlugin

/** Base handler for a toggle command linked to a platform preference.
 *
 *  The preference is updated when button is pushed. The UI element is updated
 *  when the value of the preference is modified.
 *
 *  For information on how a menu handler works see the following links:
 *  - http://eclipsesource.com/blogs/2009/01/15/toggling-a-command-contribution/
 *  - https://bugs.eclipse.org/bugs/show_bug.cgi?id=154130#c27
 *  - http://wiki.eclipse.org/Menu_Contributions
 */
abstract class AbstractToggleHandler(commandId: String, preferenceId: String)
    extends AbstractHandler
    with IElementUpdater
    with IPropertyChangeListener {

  prefStore.addPropertyChangeListener(this)

  override def execute(event: ExecutionEvent): Object = {
    toggle()
    null
  }

  override def updateElement(element: UIElement, parameters: java.util.Map[_, _]): Unit = {
    element.setChecked(isChecked)
  }

  override def dispose() = {
    super.dispose()
    prefStore.removePropertyChangeListener(this)
  }

  override def propertyChange(event: PropertyChangeEvent): Unit = {
    if (event.getProperty() == preferenceId)
      refresh()
  }

  private def isChecked: Boolean = {
    prefStore.getBoolean(preferenceId)
  }

  private def toggle(): Boolean = {
    val newValue = !prefStore.getBoolean(preferenceId)
    prefStore.setValue(preferenceId, newValue)
    newValue
  }

  private def refresh(): Unit = {
    val service = PlatformUI.getWorkbench().getService(classOf[ICommandService]).asInstanceOf[ICommandService]
    service.refreshElements(commandId, null)
  }

  private def prefStore = IScalaPlugin().getPreferenceStore
}
