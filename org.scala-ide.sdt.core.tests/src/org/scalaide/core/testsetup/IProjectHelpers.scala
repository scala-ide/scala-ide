package org.scalaide.core.testsetup

import java.io.File

import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.jdt.core.IJavaProject
import org.junit.Assert
import org.scalaide.core.IScalaProject
import org.scalaide.util.eclipse.EclipseUtils

import SDTTestUtils.enableAutoBuild
import SDTTestUtils.getErrorMessages
import SDTTestUtils.sourceWorkspaceLoc
import SDTTestUtils.workspace

/**
 * Collects implicits playing with `IProject` object
 */
trait IProjectHelpers {
  implicit def jProjectToIProject(project: IJavaProject): IProject = project.getProject
  implicit def sProjectToIProject(project: IScalaProject): IProject = project.underlying
}

/**
 * Collects operations done on 'IScalaProject' and 'IJavaProject' instances like creation, cleaning,
 * deleting.
 */
trait IProjectOperations {
  import SDTTestUtils._

  /**
   *  Runs clean build on given projects. Usually used just before test execution. Example:
   *  {{{
   *  class SomeTest extends IProjectOperations {
   *    import SomeTest._
   *
   *    @Test def shouldShowHowToUseGivenCleanMethod(): Unit = {
   *      givenCleanWorkspaceForProject(myProject.underlying)
   *
   *      // when
   *      // then
   *    }
   *  }
   *  }}}
   */
  def givenCleanWorkspaceForProjects(projects: IProject*): Unit = {
    workspace.build(IncrementalProjectBuilder.CLEAN_BUILD, new NullProgressMonitor)
    projects.foreach { project =>
      Assert.assertTrue(getErrorMessages(project).isEmpty)
    }
  }

  /**
   *  Prepares workspace with projects resources. Usually used in test companion object in setup method
   *  annotated with `@BeforeClass`. Example:
   *  {{{
   *  object SomeTest extends IProjectOperation {
   *    import SDTTestUtils._
   *    private var project: IScalaProject = _
   *
   *    @BeforeClass def setup(): Unit = {
   *      initializeProjects("bundleName", Seq("projectName")) {
   *        project = createProjectInWorkspace("projectName", withSrcRootOnly)
   *      }
   *    }
   *  }
   *  }}}
   */
  def initializeProjects(bundleName: String, projectNames: Seq[String])(postInit: => Unit): Unit = {
    enableAutoBuild(false)
    projectNames.foreach { name =>
      EclipseUtils.workspaceRunnableIn(workspace) { monitor =>
        val wspaceLoc = workspace.getRoot.getLocation
        val src = new File(sourceWorkspaceLoc(bundleName).toFile().getAbsolutePath + File.separatorChar + name)
        val dst = new File(wspaceLoc.toFile().getAbsolutePath + File.separatorChar + name)
        FileUtils.copyDirectory(src, dst)
      }
    }
    postInit
  }
}