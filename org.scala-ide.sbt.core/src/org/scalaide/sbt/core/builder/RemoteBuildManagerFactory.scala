package org.scalaide.sbt.core.builder

import org.scalaide.core.internal.builder.BuildManagerFactory
import org.scalaide.core.IScalaProject

class RemoteBuildManagerFactory extends BuildManagerFactory {
  def buildManager(project: IScalaProject) = new RemoteBuilder(project)
}