package org.scalaide.debug.internal.expression

import org.eclipse.ui.plugin.AbstractUIPlugin
import org.osgi.framework.BundleContext

object ScalaExpressionEvaluatorPlugin {
  @volatile var plugin: ScalaExpressionEvaluatorPlugin = _
}

class ScalaExpressionEvaluatorPlugin extends AbstractUIPlugin {

  override def start(context: BundleContext) {
    super.start(context)
    ScalaExpressionEvaluatorPlugin.plugin = this
  }

  override def stop(context: BundleContext) {
    try super.stop(context)
    finally ScalaExpressionEvaluatorPlugin.plugin = null
  }

}
