package scala.tools.eclipse.scalatest.launching

import scala.tools.eclipse.testsetup.TestProjectSetup
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.JavaCore

object ScalaTestProject extends TestProjectSetup("scalatest", bundleName= "org.scala-ide.sdt.scalatest.tests") {

  project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
  project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
  
  def getPackageFragment(packageName: String) = {
    val javaProject = JavaCore.create(project.underlying)
    javaProject.getPackageFragments.find(pf => pf.getElementName == packageName).getOrElse(null)
  }
}