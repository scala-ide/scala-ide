package scala.tools.eclipse
package sbtbuilder


import org.junit.Test
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.core.resources.IResource
import org.junit.Assert._
import org.eclipse.core.resources.IMarker
import scala.tools.eclipse.testsetup.SDTTestUtils
import org.eclipse.core.resources.IFile
import org.junit.Ignore
import org.junit.Before
import org.eclipse.jdt.core.ICompilationUnit

object ScalaJavaDepTest extends testsetup.TestProjectSetup("scalajavadep")

class ScalaJavaDepTest {

  import ScalaJavaDepTest._

  @Before
  def setupWorkspace {
    SDTTestUtils.enableAutoBuild(false)
  }
  
  @Test def testSimpleScalaDep() {
    println("building " + project)
    cleanProject

    val JJavaCU = compilationUnit("test/J.java")
    val SScalaCU = compilationUnit("test/S.scala")

    def getProblemMarkers= getProblemMarkersFor(JJavaCU, SScalaCU)

    val problems0 = getProblemMarkers
    assertTrue("Build errors found: " + userFriendlyMarkers(problems0), problems0.isEmpty)
    
    val originalJJava = SDTTestUtils.slurpAndClose(project.underlying.getFile("src/test/J.java").getContents)
    SDTTestUtils.changeContentOfFile(project.underlying, JJavaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], changedJJava)
    rebuild(project)
    val problems1 = getProblemMarkers
    assertTrue("One build error expected, got: " + userFriendlyMarkers(problems1), problems1.length == 1) // do more precise matching later
    
    SDTTestUtils.changeContentOfFile(project.underlying, JJavaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], originalJJava)
    rebuild(project)
    val problems2 = getProblemMarkers
    assertTrue("Build errors found: " + userFriendlyMarkers(problems2), problems2.isEmpty)
  }

  @Ignore
  @Test def testSimpleJavaDep() {
    println("building " + project)
    cleanProject

    val JJavaCU = compilationUnit("test/J.java")
    val SScalaCU = compilationUnit("test/S.scala")

    def getProblemMarkers= getProblemMarkersFor(JJavaCU, SScalaCU)

    val problems0 = getProblemMarkers
    assertTrue("Build errors found: " + userFriendlyMarkers(problems0), problems0.isEmpty)
    
    val originalSScala = SDTTestUtils.slurpAndClose(project.underlying.getFile("src/test/S.scala").getContents)
    SDTTestUtils.changeContentOfFile(project.underlying, SScalaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], changedSScala)
    rebuild(project)
    val problems1 = getProblemMarkers
    assertTrue("One build error expected: " + userFriendlyMarkers(problems1), problems1.length == 1) // do more precise matching later
    
    SDTTestUtils.changeContentOfFile(project.underlying, SScalaCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], originalSScala)
    rebuild(project)
    val problems2 = getProblemMarkers
    assertTrue("Build errors found: " + userFriendlyMarkers(problems2), problems2.isEmpty)
  }  
  
  def rebuild(prj: ScalaProject) {
    println("building " + prj)
    prj.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
  }

  /**
   * Check the test case from the assembla ticket 1000607.
   * Start from a setup with a compilation error, 'fix' it in the Scala file and check that
   * the incremental compilation correctly recompile the Java file.
   */
  @Test def ticket_1000607() {
    val aClass= compilationUnit("ticket_1000607/A.scala")
    val cClass= compilationUnit("ticket_1000607/C.java")
    
    def getProblemMarkers= getProblemMarkersFor(aClass, cClass)

    // do a clean build and check the expected error
    cleanProject
    var problems= getProblemMarkers
    assertEquals("One error expected: " + userFriendlyMarkers(problems), 1, problems.size)
    
    // "fix" the scala code
    SDTTestUtils.changeContentOfFile(project.underlying, aClass.getResource().asInstanceOf[IFile], changed_ticket_1000607_A)
    
    // trigger incremental compile
    project.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
    
    // and check that the error disappeared
    problems= getProblemMarkers
    assertTrue("Unexpected error: " + userFriendlyMarkers(problems), problems.isEmpty)
  }
    
  /**
   * Return the markers for the given compilation units.
   */
  def getProblemMarkersFor(units: ICompilationUnit*): List[IMarker] = {
    units.toList.flatMap(SDTTestUtils.findProblemMarkers)
  }
  
  /**
   * Launch a clean build on the project.
   */
  private def cleanProject: Unit = {
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
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

    // added back the commented out '= {}', to make the method concrete
  lazy val changed_ticket_1000607_A = """
package ticket_1000607

trait A {
  def foo(s: String): Unit = {}
}

abstract class B extends A
"""
  
}