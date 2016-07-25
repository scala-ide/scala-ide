package org.scalaide.core.internal.builder

import org.scalaide.core.IScalaProject

/**
 * Implemented by OSGi bundles which deliver own implementations of [[EclipseBuildManager]] exposed as service.
 */
trait BuildManagerFactory {
  def buildManager(project: IScalaProject): EclipseBuildManager
}