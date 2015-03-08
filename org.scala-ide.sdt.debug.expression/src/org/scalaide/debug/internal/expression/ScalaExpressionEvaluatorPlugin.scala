package org.scalaide.debug.internal.expression

import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext

object ScalaExpressionEvaluatorPlugin {
  @volatile private var plugin: ScalaExpressionEvaluatorPlugin = _

  def apply(): ScalaExpressionEvaluatorPlugin = plugin
}

class ScalaExpressionEvaluatorPlugin extends AbstractUIPlugin {

  override def start(context: BundleContext): Unit = {
    super.start(context)
    ScalaExpressionEvaluatorPlugin.plugin = this
  }

  override def stop(context: BundleContext): Unit = {
    try super.stop(context)
    finally ScalaExpressionEvaluatorPlugin.plugin = null
  }

}
