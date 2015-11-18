package org.scalaide.core.testsetup

import java.io.File

import org.eclipse.core.resources.IFile
import org.eclipse.core.resources.IProject
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IJavaProject
import org.junit.Assert
import org.scalaide.core.IScalaProject
import org.scalaide.ui.internal.preferences.ScalaPluginSettings
import org.scalaide.util.eclipse.EclipseUtils
import org.scalaide.util.internal.SettingConverterUtil

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

  /**
   * Allows to change compilable content of file in project to uncompilable one, then some assertions can be done
   * on founds errors. Example:
   * {{{
   *   @Test def shouldFailProjectAMainScopeAndItsTestAndProjectBMacrosMainTestNotBuilt(): Unit = {
   *     givenCleanWorkspaceForProjects(projectA, projectB)
   *     whenFileInScopeIsDamaged(projectA, "/src/main", "acme", "AcmeMain.scala", changedToNonCompiling) {
   *       val expectedTwoErrors =
   *         markersMessages(findProjectProblemMarkers(projectA, errorTypes: _*).toList)
   *       val expectedThreeErrorInB =
   *         markersMessages(findProjectProblemMarkers(projectB, errorTypes: _*).toList)
   *       val errors = expectedTwoErrors ++ expectedThreeErrorInB
   *
   *       Assert.assertTrue("See what's wrong: " + errors.mkString(", "), 5 == errors.length)
   *     }
   *   }
   * }}}
   */
  def whenFileInScopeIsDamaged(project: IScalaProject, scopeRootPath: String, packageName: String, fileName: String, changedToNonCompiling: String)(thenAssertThat: => Unit): Unit = {
    val toChangeRoot = project.javaProject.findPackageFragmentRoot(new Path("/" + project.underlying.getName + scopeRootPath))
    val compilationUnit = toChangeRoot.getPackageFragment(packageName).getCompilationUnit(fileName)
    val unit = compilationUnit.getResource.asInstanceOf[IFile]
    val revert = compilationUnit.getBuffer.getContents
    try {
      changeContentOfFile(compilationUnit.getResource().asInstanceOf[IFile], changedToNonCompiling)
      workspace.build(IncrementalProjectBuilder.INCREMENTAL_BUILD, new NullProgressMonitor)

      thenAssertThat
    } finally
      changeContentOfFile(unit, revert)
  }

  /**
   * Utility to toggle `stopBuildOnErrors` flag.
   */
  def toggleStopOnErrorsProperty(project: IScalaProject, on: Boolean): Unit = {
    val stopBuildOnErrorsProperty = SettingConverterUtil.convertNameToProperty(ScalaPluginSettings.stopBuildOnErrors.name)
    project.storage.setValue(stopBuildOnErrorsProperty, on)
  }
}
