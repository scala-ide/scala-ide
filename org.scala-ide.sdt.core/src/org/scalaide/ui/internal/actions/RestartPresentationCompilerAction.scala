package org.scalaide.ui.internal.actions

import org.eclipse.core.resources.IProject
import org.scalaide.core.IScalaPlugin

class RestartPresentationCompilerAction extends AbstractPopupAction {
  override def performAction(project: IProject): Unit = {
    val scalaProject = IScalaPlugin().asScalaProject(project)
    scalaProject foreach (_.presentationCompiler.askRestart())
  }
}
