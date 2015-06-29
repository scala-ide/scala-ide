package org.scalaide.core.testsetup

import org.scalaide.core.IScalaProject
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.core.resources.IMarker
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.core.resources.IResource

trait ProjectBuilder {

  def project: IScalaProject

  def cleanProject(): Unit = {
    project.clean(new NullProgressMonitor())
  }

  def fullProjectBuild(): Unit = {
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
  }

  def allBuildErrorsOf(unit: ICompilationUnit): Array[IMarker] =
    unit.getUnderlyingResource().findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)
}