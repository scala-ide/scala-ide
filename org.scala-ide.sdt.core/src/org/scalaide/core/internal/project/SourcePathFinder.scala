package org.scalaide.core.internal.project

import org.scalaide.core.IScalaProject
import org.scalaide.core.IScalaPlugin
import sbt.internal.inc.Analysis

trait SourcePathFinder {
  def apply(project: IScalaProject, className: String): Option[String] = {
    val analyses = project.buildManager.latestAnalysis ::
      project.transitiveDependencies.toList.collect {
        case project if IScalaPlugin().asScalaProject(project).isDefined =>
          IScalaPlugin().getScalaProject(project).buildManager.latestAnalysis
      }
    analyses.collect {
      case a: Analysis =>
        val c = a.relations.definesClass(className)
        c
    }.collectFirst {
      case files if files.nonEmpty =>
        val a = files
        a
    }.flatMap { source =>
      source.headOption.map { f =>
        val a = f.getPath
        a
      }
    }
  }
}

object SourcePathFinder {
  private val sourcePathFinder = new SourcePathFinder {}

  implicit class addSourcePathFinder(p: IScalaProject) {
    def sourcePath(className: String): Option[String] = sourcePathFinder(p, className)
  }
}