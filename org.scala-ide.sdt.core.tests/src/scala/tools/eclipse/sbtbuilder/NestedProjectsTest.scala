package scala.tools.eclipse.sbtbuilder
import scala.tools.eclipse.testsetup.TestProjectSetup
import scala.tools.eclipse.ScalaProject
import org.junit.Test
import org.junit.Assert._
import scala.tools.eclipse.util.EclipseUtils
import org.eclipse.core.resources.ResourcesPlugin
import scala.tools.eclipse.ScalaPlugin
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.JavaCore
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.IPackageFragmentRoot
import scala.tools.eclipse.testsetup.SDTTestUtils
import org.eclipse.core.resources.IFile

/**
 * Test for test cases requiring nested projects (one project root is a subfolder of an other project)
 */
object NestedProjectsTest extends TestProjectSetup("nested-parent") {
  
  final val scalaProjectName= "nested-scala"

  /**
   * The nested scala project
   */
  lazy val scalaProject: ScalaProject = {
    val workspace = ResourcesPlugin.getWorkspace()
    EclipseUtils.workspaceRunnableIn(workspace) { monitor =>
      // create the project
      val newProject= workspace.getRoot().getProject(scalaProjectName)
      val projectDescription= workspace.newProjectDescription(scalaProjectName)
      projectDescription.setLocation(project.underlying.getLocation().append(scalaProjectName))
      newProject.create(projectDescription, null)
      newProject.open(null)
      JavaCore.create(newProject)
    }
    ScalaPlugin.plugin.getScalaProject(workspace.getRoot.getProject(scalaProjectName))
  }
  
  lazy val scalaSrcPackageRoot: IPackageFragmentRoot = {
    scalaProject.javaProject.findPackageFragmentRoot(new Path("/" + scalaProjectName + "/src"))
  }
}

class NestedProjectsTest {
  import NestedProjectsTest._

  /**
   * At first, the ExpandableResourceDelta was crashing when used for nested projects. This test checks that it is not
   * happening any more.
   */
  @Test
  def checkJavaCompilesInNestedProject() {
    // clean the nested project
    scalaProject.underlying.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    scalaProject.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)
    
    // update and recompile Java_01.java
    val compilationUnit= scalaSrcPackageRoot.getPackageFragment("test").getCompilationUnit("Java_01.java")
    SDTTestUtils.changeContentOfFile(project.underlying, compilationUnit.getResource().asInstanceOf[IFile], changed_test_Java_01)
    
    scalaProject.underlying.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)
    
    // if the compilation failed, the class file was not generated
    val classFile= scalaProject.underlying.getFile("bin/test/Java_01.class")
    assertTrue("Missing class file", classFile.exists())
  }
  
  // no real change, just a space after foo
  lazy val changed_test_Java_01 = """
package test;

public class Java_01 {
    public void foo(Scala_01 s) {
        s.foo ();
    }
}
"""

}