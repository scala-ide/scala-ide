package org.scalaide.core.internal.project

import org.scalaide.core.IScalaProject
import org.scalaide.core.IScalaPlugin
import sbt.internal.inc.Analysis

trait SourcePathFinder {
  def sourcePath(project: IScalaProject, className: String): Option[String] = {
    val analyses = project.buildManager.latestAnalysis ::
      project.transitiveDependencies.toList.collect {
        case project if IScalaPlugin().asScalaProject(project).isDefined =>
          IScalaPlugin().getScalaProject(project).buildManager.latestAnalysis
      }
    analyses.collectFirst {
      case a: Analysis => a.relations.definesClass(className)
    }.flatMap { _.headOption.map { _.getPath } }
  }
}