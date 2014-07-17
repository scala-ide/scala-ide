package org.scalaide.ui.internal.preferences

import org.eclipse.core.runtime.preferences._
import org.eclipse.core.resources._
import org.eclipse.ui.preferences.ScopedPreferenceStore

/** A wrapper around a ScopedPreferenceStore that searches in the InstanceScope and ConfigurationScope
 *  after the user-provided context.
 *
 *  Note that if context is indeed an instance of IScopeContext that is a ProjectScope,
 *  this is done automatically by Kepler, though not by Juno.
 *
 *  TODO: delete this once we drop Juno support.
 *
 */
class PropertyStore(val context: IScopeContext, val pageId: String)
  extends ScopedPreferenceStore(context, pageId) {
  this.setSearchContexts(Array(context, InstanceScope.INSTANCE, ConfigurationScope.INSTANCE))

  def isProjectSpecific(): Boolean = context.isInstanceOf[ProjectScope]
  lazy val projectNode = context.getNode(pageId)

  // Cribbed from ScopedPreferenceStore, aimed at removing equality-with default value checking
  // to allow cascades of overriding values.
  private def putToNode[T](getter: String => T)(setter: (String,T) => Unit)(name: String, value:T): Unit = {
    val oldValue = getter(name)
    if (oldValue == value) return
    try {
      silentRunning = true // Turn off updates from the store
      setter(name, value)
      firePropertyChangeEvent(name, oldValue, value)
    } finally {
      silentRunning = false // Restart listening to preferences
    }
  }

  override def setValue(name: String, value: Double): Unit =
    putToNode(getDouble)(projectNode.putDouble)(name, value)

  override def setValue(name: String, value: Float): Unit =
    putToNode(getFloat)(projectNode.putFloat)(name, value)

  override def setValue(name: String, value: Int): Unit =
    putToNode(getInt)(projectNode.putInt)(name, value)

  override def setValue(name: String, value: Long): Unit =
    putToNode(getLong)(projectNode.putLong)(name, value)

  override def setValue(name: String, value: String) {
    // Do not turn on silent running here as Strings are propagated
    projectNode.put(name, value)
  }

  override def setValue(name: String, value: Boolean): Unit =
    putToNode(getBoolean)(projectNode.putBoolean)(name, value)
}