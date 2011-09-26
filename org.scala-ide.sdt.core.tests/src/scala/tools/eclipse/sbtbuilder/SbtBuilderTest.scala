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

object SbtBuilderTest extends testsetup.TestProjectSetup("builder")

class SbtBuilderTest {

  import SbtBuilderTest._
  
  @Test def testSimpleBuild() {
    println("building " + project)
    project.clean(new NullProgressMonitor())
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
    
    val units = List(compilationUnit("test/ja/JClassA.java"), compilationUnit("test/sc/ClassA.scala"))
    val noErrors = units.forall { unit =>
      val problems = unit.getUnderlyingResource().findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)
      println("problems: %s: %s".format(unit, problems.toList))
      problems.isEmpty
    }
    
    Assert.assertTrue("Build errors found", noErrors)
  }
  
  def rebuild(prj: ScalaProject): List[IMarker] = {
    println("building " + prj)
    prj.clean(new NullProgressMonitor())
    prj.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)

    val units = List(compilationUnit("test/ja/JClassA.java"), compilationUnit("test/sc/ClassA.scala"), compilationUnit("test/dependency/FooClient.scala"))
    units.flatMap { unit =>
      val problems = unit.getUnderlyingResource().findMarkers(IJavaModelMarker.JAVA_MODEL_PROBLEM_MARKER, true, IResource.DEPTH_INFINITE)
      println("problems: %s: %s".format(unit, problems.toList))
      problems
    }
  }
  
  @Test @Ignore def dependencyTest() {
    object depProject extends testsetup.TestProjectSetup("builder-sub")
    
    val problemsDep = rebuild(depProject.project)
    val problemsOrig = rebuild(project)
    Assert.assertTrue("Should succeed compilation", problemsOrig.isEmpty)
    
    val fooCU = depProject.compilationUnit("subpack/Foo.scala")
    println("IFile: " + fooCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile])
    SDTTestUtils.changeContentOfFile(depProject.project.underlying, fooCU.getResource().getAdapter(classOf[IFile]).asInstanceOf[IFile], changedFooScala)
    
    rebuild(depProject.project)
    val problems = rebuild(project)
    
    Assert.assertEquals("Should have one problem", 1, problems.size)
    Assert.assertEquals("Error should be in FooClient.scala", problems(0).getResource(), compilationUnit("test/dependency/FooClient.scala").getResource())
  }
  
  lazy val changedFooScala = """
    package subpack

class Foo1
"""
}
