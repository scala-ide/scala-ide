package org.scalaide.ui.internal.preferences

import scala.tools.nsc.Settings

import org.eclipse.core.resources.IProject
import org.eclipse.core.runtime.IPath
import org.scalaide.core.internal.ScalaPlugin
import org.scalaide.core.internal.project.CompileScope
import org.scalaide.ui.internal.preferences.IDESettings.Box

object ScopesSettings extends Settings {
  val TabTitle = "Scopes Settings"
  private val NamePrefix = "-"
  private val EmptyString = ""

  private def choices = CompileScope.scopesInCompileOrder.map { _.name }.toList

  private def makeSettings(project: IProject): List[Settings#Setting] = {
    ScalaPlugin().asScalaProject(project).map { scalaProject =>
      scalaProject.sourceFolders.map { srcFolder =>
        val srcFolderRelativeToProject = srcFolder.makeRelativeTo(project.getLocation)
        val srcName = makeKey(srcFolderRelativeToProject)
        ChoiceSetting(srcName, helpArg = EmptyString, descr = EmptyString, choices, findDefaultScope(srcFolderRelativeToProject))
      }
    }.getOrElse(Nil).toList.sortBy { _.name }
  }

  def makeKey(srcFolderRelativeToProject: IPath): String =
    NamePrefix + srcFolderRelativeToProject.segments.mkString("/")

  private def findDefaultScope(srcFolderRelativeToProject: IPath): String =
    CompileScope.scopesInCompileOrder.find { _.isValidSourcePath(srcFolderRelativeToProject) }.get.name

  def buildScopesSettings(project: Option[IProject]): Box =
    if (project.isEmpty) Box(TabTitle, Nil) else Box(TabTitle, makeSettings(project.get))
}
