package org.scalaide.ui.internal.editor.decorators

import org.eclipse.jface.text.IPaintPositionManager
import org.eclipse.jface.text.IPainter
import org.eclipse.jface.text.source.ISourceViewer
import org.eclipse.jface.util.IPropertyChangeListener
import org.eclipse.jface.util.PropertyChangeEvent
import org.eclipse.swt.events.PaintEvent
import org.eclipse.swt.events.PaintListener
import org.scalaide.core.ScalaPlugin

/**
 * This provides a painter that already handles all state necessary to detect if
 * the painter is active and enabled. Enabled means that its preference value
 * is set and active means that it is not temporarily disabled.
 */
abstract class EditorPainter(viewer: ISourceViewer, enablePreference: String) extends AnyRef
    with IPainter with PaintListener with IPropertyChangeListener {

  protected val widget = viewer.getTextWidget()
  protected val store = ScalaPlugin.prefStore

  private var isActive = false
  private var isEnabled = store.getBoolean(enablePreference)

  loadPreferences()

  final override def paint(reason: Int): Unit = {
    if (!isActive) {
      isActive = true
      store.addPropertyChangeListener(this)
      widget.addPaintListener(this)
      widget.redraw()
    }
    paintByReason(reason)
  }

  final override def paintControl(e: PaintEvent): Unit = {
    if (isPainterEnabled) {
      paintByEvent(e)
    }
  }

  final override def deactivate(redraw: Boolean): Unit = {
    if (isActive) {
      isActive = false
      widget.removePaintListener(this)
      store.removePropertyChangeListener(this)
      if (redraw)
        widget.redraw()
    }
  }

  final override def propertyChange(e: PropertyChangeEvent): Unit = {
    isEnabled = store.getBoolean(enablePreference)
    loadPreferences()
    widget.redraw()
  }

  override def setPositionManager(manager: IPaintPositionManager): Unit = {}

  protected final def isPainterEnabled: Boolean =
    isEnabled

  /**
   * This method has to be implemented by subclasses to ensure that all preference
   * values they rely on are correctly loaded at class construction.
   *
   * Furthermore, this method is called each time the preference store changes,
   * thus a subclass needs to ensure that changed values are correlty reloaded.
   *
   * '''Important:''' This method is called in the constructor in order to initalize
   * all preference values correctly. Ensure that the initalization order of the
   * subclass is correctly resolved.
   */
  def loadPreferences(): Unit

  /**
   * This method is called when [[paint]] is called. The difference is that it
   * must not handle the active state of the painter.
   */
  def paintByReason(reason: Int): Unit

  /**
   * This method is called when [[paintControl]] is called, when the component
   * is enabled and when it is active.
   */
  def paintByEvent(e: PaintEvent): Unit
}