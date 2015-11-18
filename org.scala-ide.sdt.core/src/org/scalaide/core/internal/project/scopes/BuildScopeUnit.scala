package org.scalaide.core.internal.project.scopes

import java.io.File

import scala.tools.nsc.Settings

import org.eclipse.core.resources.IContainer
import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IResource
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.SubMonitor
import org.eclipse.jdt.core.IJavaModelMarker
import org.scalaide.core.IScalaProject
import org.scalaide.core.SdtConstants
import org.scalaide.core.internal.builder.EclipseBuildManager
import org.scalaide.core.internal.builder.zinc.EclipseSbtBuildManager
import org.scalaide.core.internal.project.CompileScope
import org.scalaide.ui.internal.preferences.ScalaPluginSettings
import org.scalaide.ui.internal.preferences.ScopesSettings
import org.scalaide.util.internal.SettingConverterUtil

import sbt.inc.Analysis
import sbt.inc.IncOptions

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

  private def managesSrcFolder(src: IContainer): Boolean =
    managesSrcFolder(src.getProjectRelativePath)

  private def managesSrcFolder(srcProjectRelativePath: IPath): Boolean = {
    val srcFolderKey = ScopesSettings.makeKey(srcProjectRelativePath)
    val srcFolderProperty = SettingConverterUtil.convertNameToProperty(srcFolderKey)
    val assignedScopeName = owningProject.storage.getString(srcFolderProperty)
    def isAssignedScopeThisScope = scope.name == assignedScopeName
    def isUnassignedToAnyScopeAndValidSourcePath = assignedScopeName.isEmpty && scope.isValidSourcePath(srcProjectRelativePath)
    isAssignedScopeThisScope || isUnassignedToAnyScopeAndValidSourcePath
  }

  private def addThemToClasspath = owningProject.sourceOutputFolders.collect {
    case (src, out) if !managesSrcFolder(src) => out.getLocation
  }

  private def srcOutputs = owningProject.sourceOutputFolders.collect {
    case entry @ (src, out) if managesSrcFolder(src) => entry
  }

  def sources: Seq[IContainer] = srcOutputs.unzip._1

  override def clean(implicit monitor: IProgressMonitor): Unit = delegate.clean

  override def build(addedOrUpdated: Set[IFile], removed: Set[IFile], monitor: SubMonitor): Unit = {
    hasInternalErrors = if (areDependedUnitsBuilt || doesContinueBuildOnErrors) {
      def javaHasErrors: Boolean = {
        val SeverityNotSet = -1
        owningProject.underlying.findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE).exists { marker =>
          val severity = marker.getAttribute(IMarker.SEVERITY, SeverityNotSet)
          severity == IMarker.SEVERITY_ERROR && managesSrcFolder(marker.getResource.getLocation)
        }
      }
      delegate.build(scopeFilesToCompile(addedOrUpdated), toCompile(removed), monitor)
      delegate.hasErrors || javaHasErrors
    } else {
      true
    }
  }

  private def doesContinueBuildOnErrors = {
    val stopBuildOnErrorsProperty = SettingConverterUtil.convertNameToProperty(ScalaPluginSettings.stopBuildOnErrors.name)
    !owningProject.storage.getBoolean(stopBuildOnErrorsProperty)
  }

  private def areDependedUnitsBuilt =
    !dependentUnitInstances.exists { _.hasErrors }

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
        managesSrcFolder(sourceFolder) => this
    }
  override def buildErrors: Set[IMarker] = sources.flatMap {
    _.findMarkers(SdtConstants.ProblemMarkerId, true, IResource.DEPTH_INFINITE)
  }.toSet
}

private case class ScopeFilesToCompile(toCompile: Set[IFile] => Set[IFile], owningProject: IScalaProject) {
  private var run: Set[IFile] => Set[IFile] = once
  private def once(sources: Set[IFile]): Set[IFile] = {
    run = forever
    toCompile(owningProject.allSourceFiles)
  }
  private def forever(sources: Set[IFile]): Set[IFile] = toCompile(sources) ++ getValidJavaSourcesOfThisScope

  def apply(sources: Set[IFile]): Set[IFile] = run(sources)

  private def getValidJavaSourcesOfThisScope: Set[IFile] = {
    val Dot = 1
    toCompile(owningProject.allSourceFiles
      .filter { _.getLocation.getFileExtension == SdtConstants.JavaFileExtn.drop(Dot) })
  }
}

