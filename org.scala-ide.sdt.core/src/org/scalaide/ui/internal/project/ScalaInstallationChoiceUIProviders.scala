package org.scalaide.ui.internal.project

import org.eclipse.jface.viewers.IStructuredContentProvider
import org.scalaide.core.internal.project.ScalaInstallation.resolve
import org.eclipse.jface.viewers.Viewer
import org.scalaide.core.IScalaInstallationChoice
import org.scalaide.util.internal.CompilerUtils.shortString

trait ScalaInstallationChoiceUIProviders {

  def itemTitle(): String

  class ContentProvider extends IStructuredContentProvider {
    override def dispose(): Unit = {}

    override def inputChanged(viewer: Viewer, oldInput: Any, newInput: Any): Unit = {}

    override def getElements(input: Any): Array[Object] = {
      input match {
        case l: List[_] =>
          l.asInstanceOf[List[Object]].toArray
      }
    }
  }

  class LabelProvider extends org.eclipse.jface.viewers.LabelProvider {

    override def getText(element: Any): String = element match {
      case ch: IScalaInstallationChoice => ch.marker match {
        case Left(scalaVersion) => s"Latest ${shortString(scalaVersion)} bundle (dynamic)"
        case Right(hashcode) => s"Fixed $itemTitle : ${resolve(ch) map (_.version.unparse) getOrElse " none "}"
      }
      case _ => "[ unparseable ]"
    }
  }

}
