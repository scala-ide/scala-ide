package org.scalaide.core.classpath

import scala.tools.nsc.settings.ScalaVersion
import org.eclipse.core.runtime.Path
import org.eclipse.jdt.core.IClasspathContainer
import org.eclipse.jdt.core.JavaCore
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.scalaide.core.IScalaPlugin
import org.scalaide.core.IScalaProject
import org.scalaide.util.internal.CompilerUtils
import org.scalaide.util.eclipse.EclipseUtils
import org.eclipse.core.runtime.IPath
import java.io.File
import org.eclipse.core.runtime.NullProgressMonitor
import org.junit.AfterClass
import org.scalaide.core.SdtConstants
import org.scalaide.core.testsetup.SDTTestUtils
import org.scalaide.core.internal.project.ScalaProject

object ClasspathContainersTests {
  private var projects: List[IScalaProject] = List()

  @AfterClass
  final def deleteProject(): Unit = {
    SDTTestUtils.deleteProjects(projects: _*)
  }
}

class ClasspathContainersTests {
  import ClasspathContainersTests.projects

  val libraryId = SdtConstants.ScalaLibContId
  def getLibraryContainer(project: IScalaProject) = JavaCore.getClasspathContainer(new Path(libraryId), project.javaProject)

  def createProject(): ScalaProject = {
    val project = SDTTestUtils.internalCreateProjectInWorkspace(s"compiler-settings${projects.size}", true)
    projects = project :: projects
    project
  }

  val currentScalaVer = IScalaPlugin().scalaVersion match {
      case CompilerUtils.ShortScalaVersion(major, minor) => {
        f"$major%d.$minor%d"
      }
      case _ => "none"
  }

  val previousScalaVer = IScalaPlugin().scalaVersion match {
      case CompilerUtils.ShortScalaVersion(major, minor) => {
        // This is technically incorrect for an epoch change, but the Xsource flag won't be enough to cover for that anyway
        val lesserMinor = minor - 1
        f"$major%d.$lesserMinor%d"
      }
      case _ => "none"
  }

  def onlyOneContainer(project: IScalaProject, path: IPath) = (project.javaProject.getRawClasspath() filter (_.getPath() == path)).size == 1

  def extensionallyEqual(c1: IClasspathContainer, c2: IClasspathContainer) = {
    val sameEntries = c1.getClasspathEntries().toSet == c2.getClasspathEntries().toSet

    val sameDesc = (c1.getDescription() == c2.getDescription())
    val sameKind = (c1.getKind() == c2.getKind())
    val samePath = (c1.getPath() == c2.getPath())

    sameEntries && sameDesc && sameKind && samePath
  }

  @After
  def deleteProjects(): Unit = {
    EclipseUtils.workspaceRunnableIn(EclipseUtils.workspaceRoot.getWorkspace) { _ =>
      projects foreach { project =>
        project.underlying.delete(true, null)
        (new File(IScalaPlugin().getStateLocation().toFile(), project.underlying.getName + new Path(libraryId).toPortableString() + ".container")).delete()
      }
    }
    projects = List()
  }

  @Test
  def kind_for_default_container(): Unit = {
    val project = createProject()
    val cc = getLibraryContainer(project)
    assertTrue("The default scala lib container should be of sys library type", cc.getKind() == IClasspathContainer.K_SYSTEM)
  }

  @Test
  def default_containers_same(): Unit = {
    val project1 = createProject()
    val project2 = createProject()
    val projectC1 = getLibraryContainer(project1)
    val projectC2 = getLibraryContainer(project2)
    assertTrue("Two scala projects should have the same default lib container", extensionallyEqual(projectC1, projectC2))
  }

  @Test
  def container_after_sourcelevel_same_kind(): Unit = {
    val project = createProject()
    val container_before = getLibraryContainer(project)
    project.setDesiredSourceLevel(ScalaVersion(previousScalaVer), "explicit call container_after_sourcelevel_same_kind")
    val container_after = getLibraryContainer(project)
    assertTrue("The desired Source Level should not change the kind of the scala library container", container_before.getKind() == container_after.getKind())
  }

  @Test
  def default_is_current_Version(): Unit = {
    val project = createProject()
    val container = getLibraryContainer(project)
    val desc = container.getDescription()
    assertTrue(s"The default container should contain the current scala version. Found $desc, expected $currentScalaVer", desc.contains(currentScalaVer))
  }

  @Test
  def only_one_default(): Unit = {
    val project = createProject()
    val container = getLibraryContainer(project)
    val containerSize = (project.javaProject.getRawClasspath() filter (_.getPath() == container.getPath())).size
    assertTrue(s"Only one library container by default, found $containerSize", onlyOneContainer(project, container.getPath()))
  }

  @Test
  def only_one_after_sourcelevel(): Unit = {
    val project = createProject()
    project.setDesiredSourceLevel(ScalaVersion(previousScalaVer), "explicit call only_one_after_sourcelevel")
    val container = getLibraryContainer(project)
    assertTrue("Only one library container after sourceLevel", onlyOneContainer(project, container.getPath()))
  }

  @Test
  def source_level_doesnt_pollute_neighboring_classpaths(): Unit = {
    val project1 = createProject()
    val container_before = getLibraryContainer(project1)
    val project2 = createProject()
    project2.setDesiredSourceLevel(ScalaVersion(previousScalaVer), "explicit call :source level doesnt pollute neighboring")
    val container_after = getLibraryContainer(project1)
    assertTrue("Modifying source level on a project shouldn't modify classpath containers from neighboring projects", container_before == container_after)
  }

  @Test
  def classpath_container_kept_after_close(): Unit = {
    val project1 = createProject()
    // make sure we don't keep the default container here
    project1.setDesiredSourceLevel(ScalaVersion(previousScalaVer), "explicit call : classpath container kept after close")
    val container_before = getLibraryContainer(project1)
    EclipseUtils.workspaceRunnableIn(EclipseUtils.workspaceRoot.getWorkspace) { _ =>
      project1.underlying.close(null)
      project1.underlying.open(null)
    }
    val container_after = getLibraryContainer(project1)
    assertTrue("A modified classpath container should be kept after closing", container_before == container_after)
  }

  @Test
  def source_level_reversal_reverses_container_to_older(): Unit = {
    val project = createProject()
    // making this independent of whatever the default is
    project.setDesiredSourceLevel(IScalaPlugin().scalaVersion, "explicit initialization of source_level_reversal_to_older")
    val reversalReason = "explicit call : source level reversal to older"
    val container_before = getLibraryContainer(project)

    project.setDesiredSourceLevel(ScalaVersion(previousScalaVer), reversalReason)
    project.setDesiredSourceLevel(IScalaPlugin().scalaVersion, reversalReason)
    val container_after = getLibraryContainer(project)

    assertTrue("Going to an older source level and back again should set the original container", extensionallyEqual(container_before, container_after))
  }

  @Test
  def source_level_reversal_reverses_container_to_newer(): Unit = {
    if (IScalaPlugin().scalaVersion >= ScalaVersion("2.11.0")) {val project = createProject()
    val reversalReason = "explicit call : source level reversal to newer"
    project.setDesiredSourceLevel(ScalaVersion(previousScalaVer), reversalReason)
    val container_before = getLibraryContainer(project)
    project.setDesiredSourceLevel(IScalaPlugin().scalaVersion, reversalReason)
    project.setDesiredSourceLevel(ScalaVersion(previousScalaVer), reversalReason)
    val container_after = getLibraryContainer(project)
      assertTrue("Going to an older source level and back again should set the original container", extensionallyEqual(container_before, container_after))
    }
  }

}
