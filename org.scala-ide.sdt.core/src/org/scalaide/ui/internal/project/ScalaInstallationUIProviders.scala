package org.scalaide.ui.internal.project

import org.eclipse.jface.viewers.IStructuredContentProvider
import org.scalaide.core.internal.project.ScalaInstallation
import org.scalaide.core.internal.project.MultiBundleScalaInstallation
import org.scalaide.core.internal.project.BundledScalaInstallation
import org.eclipse.jface.viewers.Viewer
import org.scalaide.core.internal.project.LabeledScalaInstallation

trait ScalaInstallationUIProviders {

  val labels = Array("built-in", "built-in", "unknown")

  def getDecoration(si: ScalaInstallation): String = {
    si match {
        case s: BundledScalaInstallation =>
          s"$itemTitle: ${s.version.unparse} (${labels(0)})"
        case s: MultiBundleScalaInstallation =>
          s"$itemTitle: ${s.version.unparse} (${labels(1)})"
        case s: LabeledScalaInstallation =>
          s"${s.getName().getOrElse("")}: ${s.version.unparse}"
        case s: ScalaInstallation =>
          s"$itemTitle: ${s.version.unparse} (${labels(2)})"
      }
  }

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

   override def getText(element: Any): String = PartialFunction.condOpt(element){case si: ScalaInstallation => getDecoration(si)}.getOrElse("")
  }

}
