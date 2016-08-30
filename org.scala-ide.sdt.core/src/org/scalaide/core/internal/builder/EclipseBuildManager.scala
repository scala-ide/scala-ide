package org.scalaide.core.internal.builder

import java.io.File

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.SubMonitor

/**
 * Abstraction which exposes sbt compiler to eclipse.
 */
trait EclipseBuildManager {
  def build(addedOrUpdated: Set[IFile], removed: Set[IFile], monitor: SubMonitor): Unit

  /** Has build errors? Only valid if the project has been built before. */
  @volatile protected var hasInternalErrors: Boolean = false

  /** <code>true</code> says that compiler requires a sources reload. */
  def invalidateAfterLoad: Boolean

  /** Can be used to clean an compiler's internal state. */
  def clean(implicit monitor: IProgressMonitor): Unit

  /** Says about a compilation result. */
  def hasErrors: Boolean = hasInternalErrors

  /** Says if underlying compiler is able to find out and add dependencies to build path. */
  def canTrackDependencies: Boolean

  /**
   * Finds build manager which built given file
   * @return `Option[EclipseBuildManager]` when found or `None` otherwise
   */
  def buildManagerOf(outputFile: File): Option[EclipseBuildManager]

  /** Returns error markers on underlying resources. */
  def buildErrors: Set[IMarker]
}
