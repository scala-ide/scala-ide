package org.scalaide.core.internal.project

import org.scalaide.core.internal.builder.BuildManagerFactory
import org.scalaide.core.IScalaProject
import org.scalaide.core.internal.builder.EclipseBuildManager

class SbtScopesBuildManagerFactory extends BuildManagerFactory {
  def buildManager(project: IScalaProject): EclipseBuildManager = new SbtScopesBuildManager(project)
}