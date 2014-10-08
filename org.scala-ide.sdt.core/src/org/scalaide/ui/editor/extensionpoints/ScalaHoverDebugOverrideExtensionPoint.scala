package org.scalaide.ui.editor.extensionpoints

import org.eclipse.core.runtime.Platform
import org.eclipse.jface.text.ITextHover
import org.scalaide.core.internal.jdt.model.ScalaCompilationUnit
import org.scalaide.util.eclipse.EclipseUtils

object ScalaHoverDebugOverrideExtensionPoint {
  final val EXTENSION_POINT_ID = "org.scala-ide.sdt.core.scalaHoverDebugOverride"

  def hoverFor(scu: ScalaCompilationUnit): Option[ITextHover] = extensionHoverFactory map {_ createFor scu}

  private lazy val extensionHoverFactory: Option[TextHoverFactory] = {
    // A max of 1 extension is allowed for this extension point. In case more than one is available, only one
    // will be used, non-deterministically.
    Platform.getExtensionRegistry.getConfigurationElementsFor(EXTENSION_POINT_ID).headOption flatMap { configElem =>
      EclipseUtils.withSafeRunner("Couldn't create extension of scalaHoverDebugOverride extension point") {
        configElem.createExecutableExtension("hoverFactoryClass").asInstanceOf[TextHoverFactory]
      }
    }
  }
}

/**
 * The interface that an extension should implement.
 */
trait TextHoverFactory {
  def createFor(scu: ScalaCompilationUnit): ITextHover
}
