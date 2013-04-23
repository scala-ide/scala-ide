/*
 * Copyright 2005-2010 LAMP/EPFL
 */
// $Id$

package scala.tools.eclipse

import org.eclipse.core.resources.{ IFile, IMarker }
import org.eclipse.core.runtime.{ IProgressMonitor, SubMonitor }

import scala.collection.mutable.HashSet

import scala.tools.nsc.{ Global, Settings }
import scala.tools.nsc.io.AbstractFile
import scala.tools.nsc.reporters.Reporter

import scala.tools.eclipse.util.{ EclipseResource, FileUtils }
import scala.tools.eclipse.buildmanager.{BuildReporter}

trait EclipseBuildManager {
  def build(addedOrUpdated: Set[IFile], removed: Set[IFile], monitor: SubMonitor): Unit
  var depFile: IFile
  
  /** Has build errors? Only valid if the project has been built before. */
  @volatile var hasErrors: Boolean = false
  
  def invalidateAfterLoad: Boolean
  def clean(implicit monitor: IProgressMonitor): Unit
}