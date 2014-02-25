package org.scalaide.ui.internal.actions

import org.eclipse.core.resources.IProject
import org.scalaide.core.ScalaPlugin

class RestartPresentationCompilerAction extends AbstractPopupAction {
  override def performAction(project: IProject): Unit = {
    val scalaProject = ScalaPlugin.plugin.asScalaProject(project)
    scalaProject foreach (_.presentationCompiler.askRestart())
  }
}
