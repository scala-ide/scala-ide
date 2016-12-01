package org.scalaide.core
package sbtbuilder

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IMarker
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.IPath
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.ICompilationUnit
import org.eclipse.jdt.core.JavaCore
import org.junit.AfterClass
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.scalaide.core.testsetup.IProjectHelpers
import org.scalaide.core.testsetup.IProjectOperations
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.util.when
import org.eclipse.jdt.core.IPackageFragmentRoot
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.core.resources.IResource

object ScalaJavaDepTwoScopesTest extends IProjectOperations {
  import org.scalaide.core.testsetup.SDTTestUtils._
  private val projectName = "scalajavadeptwoscopes"
  private var project: IScalaProject = _
  private val bundleName = "org.scala-ide.sdt.core.tests"
  private lazy val mainPackage = project.javaProject.findPackageFragmentRoot(new Path(s"/$projectName/src/main/"))
  private lazy val testPackage = project.javaProject.findPackageFragmentRoot(new Path(s"/$projectName/src/test/"))

  private val withSrcOutputStructure: SrcPathOutputEntry = (project, jProject) => {
    val mainSourceFolder = project.getFolder("/src/main")
    val testSourceFolder = project.getFolder("/src/test")
    val mainOutputFolder = project.getFolder("/target/main")
    val testOutputFolder = project.getFolder("/target/test")
    val srcOuts = List(
      mainSourceFolder -> mainOutputFolder,
      testSourceFolder -> testOutputFolder)
    srcOuts.map {
      case (src, out) => JavaCore.newSourceEntry(
        jProject.getPackageFragmentRoot(src).getPath,
        Array[IPath](),
        jProject.getPackageFragmentRoot(out).getPath)
    }
  }

  private def compilationUnit(packageRoot: IPackageFragmentRoot)(path: String): ICompilationUnit = {
    val segments = path.split("/")
    packageRoot.getPackageFragment(segments.init.mkString(".")).getCompilationUnit(segments.last)
  }

  @BeforeClass def setup(): Unit = {
    initializeProjects(bundleName, Seq(projectName)) {
      project = createProjectInWorkspace(projectName, withSrcOutputStructure)
      assertNotNull(mainPackage)
      assertNotNull(testPackage)
      mainPackage.open(null)
      testPackage.open(null)
    }
  }

  @AfterClass def cleanup(): Unit = {
    mainPackage.close()
    testPackage.close()
    SDTTestUtils.deleteProjects(project)
  }
}

class ScalaJavaDepTwoScopesTest extends IProjectOperations with IProjectHelpers {
  import ScalaJavaDepTwoScopesTest._
  import org.scalaide.core.testsetup.SDTTestUtils._

  @Before
  def setupWorkspace(): Unit = {
    enableAutoBuild(false)
  }

  @Test def testWhenJavaInMainScopeProducesWarning(): Unit = {
    println("building " + project)
    cleanProject()

    val mainJavaCU = compilationUnit(mainPackage)("acme/J.java")
    val testScalaCU = compilationUnit(testPackage)("acme/S.scala")
    val originalJava = SDTTestUtils.slurpAndClose(project.underlying.getFile("src/main/acme/J.java").getContents)

    def getProblemMarkers = getProblemMarkersFor(mainJavaCU, testScalaCU)

    when("initialize project") `then` "is no java problem" in {
      val problems = getProblemMarkers
      assertTrue("No build problem expected, found: " + markersMessages(problems), problems.isEmpty)
    }

    when("change java") `then` "expect one java problem of warning severity" in {
      SDTTestUtils.changeContentOfFile(mainJavaCU.getResource().getAdapter(classOf[IFile]), changedWarningJava)
      rebuild(project)
      val problems = getProblemMarkers
      assertTrue("One build warning problem expected, found: " + markersMessages(problems),
        problems.length == 1 &&
          problems.filter(_.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO) == IMarker.SEVERITY_WARNING).length == 1)
    }

    when("revert java change") `then` "expect no problem" in {
      SDTTestUtils.changeContentOfFile(mainJavaCU.getResource().getAdapter(classOf[IFile]), originalJava)
      rebuild(project)
      val problems = getProblemMarkers
      assertTrue("No build problems expected, found: " + markersMessages(problems), problems.isEmpty)
    }
  }

  @Test def testWhenJavaInMainScopeProducesError(): Unit = {
    println("building " + project)
    cleanProject()

    val mainJavaCU = compilationUnit(mainPackage)("acme/J.java")
    val testScalaCU = compilationUnit(testPackage)("acme/S.scala")
    val originalJava = SDTTestUtils.slurpAndClose(project.underlying.getFile("src/main/acme/J.java").getContents)

    def getProblemMarkers = getProblemMarkersFor(mainJavaCU, testScalaCU)

    when("initialize project") `then` "is no java problem" in {
      val problems = getProblemMarkers
      assertTrue("No build problem expected, found: " + markersMessages(problems), problems.isEmpty)
    }

    when("change java") `then` "expect one java problem of error severity" in {
      SDTTestUtils.changeContentOfFile(mainJavaCU.getResource().getAdapter(classOf[IFile]), changeErrorJava)
      rebuild(project)
      val problems = getProblemMarkers
      assertTrue("One build error problem expected, found: " + markersMessages(problems),
        problems.length == 1 &&
          problems.filter(_.getAttribute(IMarker.SEVERITY, IMarker.SEVERITY_INFO) == IMarker.SEVERITY_ERROR).length == 1)
    }

    when("revert java change") `then` "expect no problem" in {
      SDTTestUtils.changeContentOfFile(mainJavaCU.getResource().getAdapter(classOf[IFile]), originalJava)
      rebuild(project)
      val problems = getProblemMarkers
      assertTrue("No build problems expected, found: " + markersMessages(problems), problems.isEmpty)
    }
  }

  def rebuild(prj: IScalaProject): Unit = {
    println("building " + prj)
    prj.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
  }

  /**
   * Return the markers for the given compilation units.
   */
  def getProblemMarkersFor(units: ICompilationUnit*): List[IMarker] = units.toList.flatMap { unit =>
    val javaProblems = unit.getUnderlyingResource().findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, false, IResource.DEPTH_INFINITE)
    val scalaProblems = unit.getUnderlyingResource().findMarkers(SdtConstants.ProblemMarkerId, false, IResource.DEPTH_INFINITE)
    javaProblems ++ scalaProblems
  }

  /**
   * Launch a clean build on the project.
   */
  private def cleanProject(): Unit = {
    project.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
  }

  private lazy val changedWarningJava = """
package acme;
import java.util.List;
public class J {
  public String bar(String s) {
    return s + s;
  }
  public List getList() {
    return null;
  }
}
"""

  private lazy val changeErrorJava = """
package acme;
public class J {
  public String bar(String s) {
    return s + s;
  }
  public List getList() {
    return null;
  }
}
"""
}
