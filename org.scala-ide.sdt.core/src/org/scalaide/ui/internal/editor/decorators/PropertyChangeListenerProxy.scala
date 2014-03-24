/*
 * Copyright (c) 2014 Contributor. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Scala License which accompanies this distribution, and
 * is available at http://www.scala-lang.org/node/146
 */
package org.scalaide.ui.internal.editor.decorators

import org.eclipse.jface.preference.IPreferenceStore
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import java.lang.ref.WeakReference
import org.eclipse.jface.preference._

object PropertyChangeListenerProxy {

  def apply(listener: IPropertyChangeListener, stores: IPreferenceStore*) =
    new PropertyChangeListenerProxy(new WeakReference(listener), stores: _*)

}

class PropertyChangeListenerProxy(listenerRef: WeakReference[IPropertyChangeListener], stores: IPreferenceStore*)
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
