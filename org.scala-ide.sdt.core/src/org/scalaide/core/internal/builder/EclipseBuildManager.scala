/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.core.resources.IFile
import org.eclipse.core.runtime.IProgressMonitor
import org.eclipse.core.runtime.SubMonitor

trait EclipseBuildManager {
  def build(addedOrUpdated: Set[IFile], removed: Set[IFile], monitor: SubMonitor): Unit
  var depFile: IFile

  /** Has build errors? Only valid if the project has been built before. */
  @volatile var hasErrors: Boolean = false

  def invalidateAfterLoad: Boolean
  def clean(implicit monitor: IProgressMonitor): Unit
}