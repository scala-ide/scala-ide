package org.scalaide.ui.internal.project

import org.eclipse.jface.viewers.IStructuredContentProvider
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.core.internal.project.MultiBundleScalaInstallation
import org.scalaide.core.internal.project.BundledScalaInstallation
import org.eclipse.jface.viewers.Viewer

trait ScalaInstallationUIProviders {

  def title() : String

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

    override def getText(element: Any): String = {
      element match {
        case s: BundledScalaInstallation =>
          s"$title: bundled ${s.version.unparse}"
        case s: MultiBundleScalaInstallation =>
          s"$title: multi bundles ${s.version.unparse}"
        case s: ScalaInstallation =>
          s"unknown ${s.version.unparse}"
      }
    }
  }

}
