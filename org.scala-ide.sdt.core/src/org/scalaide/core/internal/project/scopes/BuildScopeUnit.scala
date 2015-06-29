package org.scalaide.core.internal.project.scopes

import scala.tools.nsc.Settings
import org.eclipse.core.resources.IContainer
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.SubMonitor
import org.eclipse.jdt.core.IJavaModelMarker
import org.scalaide.core.IScalaProject
import org.scalaide.core.SdtConstants
import org.scalaide.core.internal.builder.BuildProblemMarker
import org.scalaide.core.internal.builder.EclipseBuildManager
import org.scalaide.core.internal.builder.zinc.EclipseSbtBuildManager
import org.scalaide.core.internal.project.CompileScope
import sbt.inc.Analysis
import sbt.inc.IncOptions
import java.io.File

/**
 * Manages compilation of sources for given scope.
 * @see CompileScope scopes
 */
class BuildScopeUnit(val scope: CompileScope, val owningProject: IScalaProject, settings: Settings,
  val dependentUnitInstances: Seq[BuildScopeUnit] = Seq.empty)
    extends EclipseBuildManager {

  private val delegate =
    new EclipseSbtBuildManager(owningProject, settings, Some(owningProject.underlying.getFile(".cache-" + scope.name)),
      addThemToClasspath, srcOutputs)
  private val scopeFilesToCompile = ScopeFilesToCompile(toCompile, owningProject)

  private def managesSrcFolder(src: IContainer) = scope.isValidSourcePath(src.getProjectRelativePath)

  private def addThemToClasspath = owningProject.sourceOutputFolders.collect {
    case (src, out) if !managesSrcFolder(src) => out.getLocation
  }

  private def srcOutputs = owningProject.sourceOutputFolders.collect {
    case entry @ (src, out) if managesSrcFolder(src) => entry
  }

  def sources: Seq[IContainer] = srcOutputs.unzip._1

  override def clean(implicit monitor: IProgressMonitor): Unit = delegate.clean

  override def build(addedOrUpdated: Set[IFile], removed: Set[IFile], monitor: SubMonitor): Unit = {
    hasInternalErrors = if (areDependedUnitsBuilt) {
      def javaHasErrors: Boolean = {
        val SeverityNotSet = -1
        owningProject.underlying.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE).exists { marker =>
          val severity = marker.getAttribute(IMarker.SEVERITY, SeverityNotSet)
          severity == IMarker.SEVERITY_ERROR && scope.isValidSourcePath(marker.getResource.getLocation)
        }
      }
      delegate.build(scopeFilesToCompile(addedOrUpdated), toCompile(removed), monitor)
      delegate.hasErrors || javaHasErrors
    } else {
      true
    }
  }

  private def areDependedUnitsBuilt = {
    val wrongScopes = dependentUnitInstances filter { _.hasErrors } map { _.scope }
    if (wrongScopes.nonEmpty) {
      BuildProblemMarker.create(owningProject.underlying,
        s"${owningProject.underlying.getName}'s ${scope.name} not built due to errors in dependent scope(s) ${wrongScopes.map(_.name).toSet.mkString(", ")}")
      false
    } else true
  }

  private def toCompile(sources: Set[IFile]) = (for {
    (src, _) <- srcOutputs
    source <- sources if src.getProjectRelativePath.isPrefixOf(source.getProjectRelativePath)
  } yield source).toSet

  override def canTrackDependencies: Boolean = delegate.canTrackDependencies
  override def invalidateAfterLoad: Boolean = delegate.invalidateAfterLoad
  override def latestAnalysis(incOptions: => IncOptions): Analysis =
    delegate.latestAnalysis(incOptions)

  override def buildManagerOf(outputFile: File): Option[EclipseBuildManager] =
    owningProject.sourceOutputFolders collectFirst {
      case (sourceFolder, outputFolder) if outputFolder.getLocation.toFile == outputFile &&
        scope.isValidSourcePath(sourceFolder.getProjectRelativePath) => this
    }
}

private case class ScopeFilesToCompile(toCompile: Set[IFile] => Set[IFile], owningProject: IScalaProject) {
  private var run: Set[IFile] => Set[IFile] = once
  private def once(sources: Set[IFile]): Set[IFile] = {
    run = forever
    toCompile(owningProject.allSourceFiles)
  }
  private def forever(sources: Set[IFile]): Set[IFile] = toCompile(sources) ++ resetJavaMarkers(getValidJavaSourcesOfThisScope)

  def apply(sources: Set[IFile]): Set[IFile] = run(sources)

  private def getValidJavaSourcesOfThisScope: Set[IFile] = {
    val Dot = 1
    toCompile(owningProject.allSourceFiles
      .filter { _.getLocation.getFileExtension == SdtConstants.JavaFileExtn.drop(Dot) })
  }

  private def resetJavaMarkers(javaFiles: Set[IFile]): Set[IFile] = {
    javaFiles.foreach { _.deleteMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE) }
    javaFiles
  }
}