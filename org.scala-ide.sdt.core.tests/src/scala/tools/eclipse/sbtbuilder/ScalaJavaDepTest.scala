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
    SDTTestUtils.enableAutoBuild(true)
  }
  
  @Test def testSimpleScalaDep() {
    println("building " + project)
    project.clean(new NullProgressMonitor())
    rebuild(project)

    Assert.assertTrue("Build errors found", getProblemMarkers.isEmpty)
    
    val JJavaCU = compilationUnit("test/J.java")
    val originalJJava = SDTTestUtils.slurpAndClose(project.underlying.getFile("test/J.java").getContents)
    println("IFile: " + JJavaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile])
    SDTTestUtils.changeContentOfFile(project.underlying, JJavaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], changedJJava)
    rebuild(project)
    Assert.assertTrue("One build error expected", getProblemMarkers().length == 1) // do more precise matching later
    
    val JJavaCU2 = compilationUnit("test/J.java")
    println("IFile: " + JJavaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile])
    SDTTestUtils.changeContentOfFile(project.underlying, JJavaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], originalJJava)
    Assert.assertTrue("Build errors found", getProblemMarkers().isEmpty)
  }

  @Test def testSimpleJavaDep() {
    println("building " + project)
    project.clean(new NullProgressMonitor())
    rebuild(project)

    Assert.assertTrue("Build errors found", getProblemMarkers.isEmpty)
    
    val SScalaCU = compilationUnit("test/S.scala")
    val originalSScala = SDTTestUtils.slurpAndClose(project.underlying.getFile("test/S.scala").getContents)
    println("IFile: " + SScalaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile])
    SDTTestUtils.changeContentOfFile(project.underlying, SScalaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], changedSScala)
    rebuild(project)
    Assert.assertTrue("One build error expected", getProblemMarkers().length == 1) // do more precise matching later
    
    val JJavaCU2 = compilationUnit("test/S.scala")
    println("IFile: " + SScalaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile])
    SDTTestUtils.changeContentOfFile(project.underlying, SScalaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], originalSScala)
    Assert.assertTrue("Build errors found", getProblemMarkers().isEmpty)
  }  
  
  def rebuild(prj: ScalaProject): List[IMarker] = {
    println("building " + prj)
    prj.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
    getProblemMarkers()
  }

  def getProblemMarkers(): List[IMarker] = {
    val units = compilationUnits("test/J.java", "test/S.scala").toList
    units.flatMap(SDTTestUtils.findProblemMarkers)
  }
  
  lazy val changedJJava = """
package test

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