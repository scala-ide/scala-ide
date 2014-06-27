package org.scalaide.ui.internal.project

import org.eclipse.jface.viewers.IStructuredContentProvider
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.core.internal.project.MultiBundleScalaInstallation
import org.scalaide.core.internal.project.BundledScalaInstallation
import org.eclipse.jface.viewers.Viewer
import org.scalaide.core.internal.project.LabeledScalaInstallation
import org.scalaide.core.internal.project.LabeledDirectoryScalaInstallation

trait ScalaInstallationUIProviders {

  def itemTitle(): String

  class ContentProvider extends IStructuredContentProvider {
    override def dispose(): Unit = {}

    override def inputChanged(viewer: Viewer, oldInput: Any, newInput: Any): Unit = {}

    override def getElements(input: Any): Array[Object] = {
      input match {
        case l: List[ScalaInstallation] =>
          l.toArray
      }
    }
  }

   class LabelProvider extends org.eclipse.jface.viewers.LabelProvider {

    val labels = Array("bundled", "multi bundles", "unknown")

    override def getText(element: Any): String = {
      element match {
        case s: BundledScalaInstallation =>
          s"$itemTitle: ${labels(0)} ${s.version.unparse}"
        case s: MultiBundleScalaInstallation =>
          s"$itemTitle: ${labels(1)} ${s.version.unparse}"
        case s: LabeledScalaInstallation =>
          s"$itemTitle: ${s.getName().getOrElse("")} ${s.version.unparse}"
        case s: ScalaInstallation =>
          s"$itemTitle: ${labels(2)} ${s.version.unparse}"
      }
    }
  }

}
