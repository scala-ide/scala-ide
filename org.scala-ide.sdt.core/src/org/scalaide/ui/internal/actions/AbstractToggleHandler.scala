/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.ui.internal.actions

import org.eclipse.core.commands.ExecutionEvent
import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.ui.commands.ICommandService
import org.eclipse.ui.commands.IElementUpdater
import org.eclipse.ui.PlatformUI
import org.scalaide.core.ScalaPlugin
import org.eclipse.ui.menus.UIElement
import org.scalaide.ui.internal.editor.decorators.implicits.PropertyChangeListenerProxy
import org.eclipse.core.commands.AbstractHandler
import org.scalaide.util.internal.eclipse.SWTUtils

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
abstract class AbstractToggleHandler(commandId: String, preferenceId: String) extends AbstractHandler with IElementUpdater {

  private def pluginStore: IPreferenceStore = ScalaPlugin.plugin.getPreferenceStore

  /** Call when the button is push.
   */
  def execute(event: ExecutionEvent): Object = {
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
    pluginStore.setValue(preferenceId, newValue)
    newValue
  }

  // listen change on the property regardless the source of the change (preferences page, widget linked to the handler)
  private val _listener = SWTUtils.fnToPropertyChangeListener {
    event =>
      if (event.getProperty() == preferenceId) {
        refresh()
      }
  }

  PropertyChangeListenerProxy(_listener, pluginStore).autoRegister()

  private def refresh() {
    val service = PlatformUI.getWorkbench().getService(classOf[ICommandService]).asInstanceOf[ICommandService]
    service.refreshElements(commandId, null)
  }
}
