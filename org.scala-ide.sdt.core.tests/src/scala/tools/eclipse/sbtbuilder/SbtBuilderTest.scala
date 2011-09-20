package scala.tools.eclipse
package sbtbuilder

import org.junit.Test
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.jdt.core.IJavaModelMarker
import org.eclipse.core.resources.IResource
import org.junit.Assert

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
}
