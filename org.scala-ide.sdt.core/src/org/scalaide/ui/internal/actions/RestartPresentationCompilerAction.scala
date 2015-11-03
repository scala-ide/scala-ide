package org.scalaide.ui.internal.actions

import org.eclipse.core.resources.IProject
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.core.internal.statistics.Features

class RestartPresentationCompilerAction extends AbstractPopupAction {
  override def performAction(project: IProject): Unit = {
    ScalaPlugin().statistics.incUsageCounter(Features.RestartPresentationCompiler)

    val scalaProject = IScalaPlugin().asScalaProject(project)
    scalaProject foreach (_.presentationCompiler.askRestart())
  }
}
