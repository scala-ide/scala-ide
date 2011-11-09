package scala.tools.eclipse
package sbtbuilder


import org.junit.Test
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.core.resources.IResource

import org.junit.Assert
import org.eclipse.core.resources.IMarker

import scala.tools.eclipse.testsetup.SDTTestUtils
import org.eclipse.core.resources.IFile
import org.junit.Ignore
import org.junit.Before

object ScalaJavaDepTest extends testsetup.TestProjectSetup("scalajavadep")

class ScalaJavaDepTest {

  import ScalaJavaDepTest._

  @Before
  def setupWorkspace {
    SDTTestUtils.enableAutoBuild(false)
  }
  
  @Test def testSimpleScalaDep() {
    println("building " + project)
    project.clean(new NullProgressMonitor())
    rebuild(project, false)

    val problems0 = getProblemMarkers
    Assert.assertTrue("Build errors found: " + userFriendlyMarkers(problems0), problems0.isEmpty)
    
    val JJavaCU = compilationUnit("test/J.java")
    val originalJJava = SDTTestUtils.slurpAndClose(project.underlying.getFile("src/test/J.java").getContents)
    SDTTestUtils.changeContentOfFile(project.underlying, JJavaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], changedJJava)
    rebuild(project)
    val problems1 = getProblemMarkers()
    Assert.assertTrue("One build error expected, got: " + userFriendlyMarkers(problems1), problems1.length == 1) // do more precise matching later
    
    val JJavaCU2 = compilationUnit("test/J.java")
    SDTTestUtils.changeContentOfFile(project.underlying, JJavaCU2.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], originalJJava)
    rebuild(project)
    val problems2 = getProblemMarkers()
    Assert.assertTrue("Build errors found: " + userFriendlyMarkers(problems2), problems2.isEmpty)
  }

  @Ignore
  @Test def testSimpleJavaDep() {
    println("building " + project)
    project.clean(new NullProgressMonitor())
    rebuild(project, false)

    val problems0 = getProblemMarkers()
    Assert.assertTrue("Build errors found: " + userFriendlyMarkers(problems0), problems0.isEmpty)
    
    val SScalaCU = compilationUnit("test/S.scala")
    val originalSScala = SDTTestUtils.slurpAndClose(project.underlying.getFile("src/test/S.scala").getContents)
    SDTTestUtils.changeContentOfFile(project.underlying, SScalaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], changedSScala)
    rebuild(project)
    val problems1 = getProblemMarkers()
    Assert.assertTrue("One build error expected: " + userFriendlyMarkers(problems1), problems1.length == 1) // do more precise matching later
    
    val SScalaCU2 = compilationUnit("test/S.scala")
    SDTTestUtils.changeContentOfFile(project.underlying, SScalaCU2.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], originalSScala)
    rebuild(project)
    val problems2 = getProblemMarkers()
    Assert.assertTrue("Build errors found: " + userFriendlyMarkers(problems2), problems2.isEmpty)
  }
  
  def rebuild(prj: ScalaProject, incremental: Boolean = true): List[IMarker] = {
    println("building " + prj)
    val buildType = if (incremental) IncrementalProjectBuilder.INCREMENTAL_BUILD else IncrementalProjectBuilder.FULL_BUILD
    prj.underlying.build(buildType, new NullProgressMonitor)
    getProblemMarkers()
  }

  def getProblemMarkers(): List[IMarker] = {
    val units = compilationUnits("test/J.java", "test/S.scala").toList
    units.flatMap(SDTTestUtils.findProblemMarkers)
  }
  
  def userFriendlyMarkers(markers: List[IMarker]) = markers.map(_.getAttribute(IMarker.MESSAGE))
  
  lazy val changedJJava = """
package test;

public class J {
	public static void main(String[] args) {
		new S().foo("ahoy");
	}
	public String bar1(String s) {
		return s + s;
	}
}
"""

  lazy val changedSScala = """
package test

class S {
	def foo1(s:String) { println(new J().bar(s)) } 
}
"""
}