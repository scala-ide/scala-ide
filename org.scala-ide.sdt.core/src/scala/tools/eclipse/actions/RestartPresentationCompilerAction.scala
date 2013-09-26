package scala.tools.eclipse.actions

import org.eclipse.core.resources.IProject
import scala.tools.eclipse.ScalaPlugin

class RestartPresentationCompilerAction extends AbstractPopupAction {
  override def performAction(project: IProject): Unit = {
    val scalaProject = ScalaPlugin.plugin.asScalaProject(project)
    scalaProject foreach (_.presentationCompiler.askRestart())
  }
}