package org.scalaide.core.internal.project

import org.scalaide.core.IScalaProject
import org.scalaide.core.IScalaPlugin
import sbt.internal.inc.Analysis

trait SourcePathFinder {
  private def sourceFolders(project: IScalaProject): Seq[String] = project.sourceFolders.map {
    _.makeAbsolute.toFile.getAbsolutePath
  }

  def apply(project: IScalaProject, className: String): Option[String] = {
    val analyses = (project.buildManager.latestAnalysis, sourceFolders(project)) ::
      project.transitiveDependencies.toList.collect {
        case project if IScalaPlugin().asScalaProject(project).isDefined =>
          val sproject = IScalaPlugin().getScalaProject(project)
          (sproject.buildManager.latestAnalysis, sourceFolders(sproject))
      }
    analyses.collect {
      case (a: Analysis, sourceFolders) =>
        a.relations.definesClass(className)
          .flatMap { foundRelativeSrc =>
            val path = foundRelativeSrc.getPath
            sourceFolders.collect {
              case sf if path.startsWith(path) =>
                path.substring(sf.length)
            }
          }
    }.collectFirst {
      case files if files.nonEmpty =>
        files
    }.flatMap { _.headOption }
  }
}

object SourcePathFinder {
  private val sourcePathFinder = new SourcePathFinder {}

  implicit class addSourcePathFinder(p: IScalaProject) {
    def sourcePath(className: String): Option[String] = sourcePathFinder(p, className)
  }
}