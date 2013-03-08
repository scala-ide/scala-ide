package scala.tools.eclipse.launching

import scala.tools.eclipse.util.EclipseUtils
import org.junit.After
import org.junit.Before
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.EclipseUserSimulator
import scala.tools.eclipse.ScalaPlugin
import org.junit.Test
import scala.tools.eclipse.javaelements.ScalaCompilationUnit
import org.mockito.Mockito._
import org.mockito.Matchers.any
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.eclipse.core.resources.IncrementalProjectBuilder
import org.eclipse.core.runtime.NullProgressMonitor
import org.mockito.verification.VerificationMode
import org.eclipse.debug.core.ILaunchConfiguration
import org.eclipse.debug.core.DebugPlugin
import org.junit.Assert
import org.eclipse.debug.core.ILaunchManager
import scala.tools.eclipse.testsetup.FileUtils
import scala.tools.eclipse.testsetup.SDTTestUtils
import org.junit.Ignore

@RunWith(classOf[JUnit4])
class MainClassVerifierTest {
  import MainClassVerifierTest.EmptyPackage

  protected val simulator = new EclipseUserSimulator

  private var project: ScalaProject = _

  @Before
  def createProject() {
    project = simulator.createProjectInWorkspace("main-launcher", true)
  }

  @After
  def deleteProject() {
    EclipseUtils.workspaceRunnableIn(ScalaPlugin.plugin.workspaceRoot.getWorkspace) { _ =>
      project.underlying.delete(true, null)
    }
  }

  /** This test is ignored becase there is no way to test it without seriously risking impairing the IDE
   *
   *  The launcher works by checking if there are build errors, and if yes delegating to a 'status handler'
   *  The status handler displays a dialog and decides whether to proceed or not. If the status handler
   *  extension point is not available, it always continues (`finalLaunchCheck` returns true).
   *
   *  If we add our own status handler in org.scala-ide.sdt.core.tests, we risk replacing the default
   *  dialog if the user installed the 'tests' plugin, and never seeing the warning for build errors.
   */
  @Test @Ignore("There is no way to test launching without using an extension point that intercepts all status requests")
  def reportErrorWhenMainContainsCompilationErrors() {
    val mainName = "MainWithCompilationErrors"
    val main = """
    object %s extends App {
      foo // <-- foo is an unkown identifier
    }
    """.format(mainName)

    val launchMemento = """
      <launchConfiguration type="scala.application">
        <stringAttribute key="bad_container_name" value="/main-launcher/f"/>
        <listAttribute key="org.eclipse.debug.core.MAPPED_RESOURCE_PATHS">
          <listEntry value="/main-launcher/src/MainWithCompilationErrors.scala"/>
        </listAttribute>
        <listAttribute key="org.eclipse.debug.core.MAPPED_RESOURCE_TYPES">
          <listEntry value="1"/>
        </listAttribute>
        <mapAttribute key="org.eclipse.debug.core.preferred_launchers">
          <mapEntry key="[debug]" value="scala.application.new"/>
        </mapAttribute>
        <stringAttribute key="org.eclipse.jdt.launching.MAIN_TYPE" value="MainWithCompilationErrors"/>
        <stringAttribute key="org.eclipse.jdt.launching.PROJECT_ATTR" value="main-launcher"/>
      </launchConfiguration>
"""

    createSource(EmptyPackage, mainName, main)
    SDTTestUtils.addFileToProject(project.underlying, "Main.launch", launchMemento)

    val config = DebugPlugin.getDefault().getLaunchManager().getLaunchConfiguration(project.underlying.getFile("Main.launch"))
    val delegate = new ScalaLaunchDelegate()
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)

    Assert.assertFalse("Compilation errors prevent launching",
      delegate.preLaunchCheck(config, ILaunchManager.DEBUG_MODE, new NullProgressMonitor)
        && delegate.finalLaunchCheck(config, ILaunchManager.DEBUG_MODE, new NullProgressMonitor))
  }

  @Test
  def reportErrorWhenPackageDeclarationInMainTypeDoesntMatchPhysicalLocation() {
    val pkg = "foo"
    val mainName = "Main"
    val main = "object %s extends App".format(mainName) // note: no package declaration here!

    createSource(pkg, mainName, main) // source is created in foo/ (look at the value of `pkg`)
    val mainTypeName = mainName // this is the correct fully-qualified name

    runTest(mainTypeName, main, times(1))
  }

  @Test
  def reportErrorWhenPackageDeclarationInMainTypeDoesntMatchPhysicalLocation2() {
    val sourceLocation = "foo"
    val pkg = "bar"
    val mainName = "Main"
    val main = """
      package %s
      object %s extends App
    """.format(pkg, mainName) // note: no package declaration here!

    createSource(sourceLocation, mainName, main) // source is created in foo/
    val mainTypeName = pkg + "." + mainName // this is the correct fully-qualified name

    runTest(mainTypeName, main, times(1))
  }

  @Test
  def reportErrorWhenMainClassInLaunchConfigurationIsWrong() {
    val pkg = "foo"
    val mainName = "Main"
    val main = """
    package %s
    object %s extends App
    """.format(pkg, mainName)

    createSource(pkg, mainName, main)
    val mainTypeName = mainName // this is *NOT* the correct fully-qualified name

    runTest(mainTypeName, main, times(1))
  }

  @Test
  def mainVerificationSucceed() {
    val pkg = "foo"
    val mainName = "Main"
    val main = """
    package %s
    object %s extends App
    """.format(pkg, mainName)

    createSource(pkg, mainName, main)
    val mainTypeName = pkg + "." + mainName

    runTest(mainTypeName, main, never())
  }

  @Test
  def runScalaAppOnSourceWithSeveralPackageDeclaration_t1001096() {
    val mainName = "Test1"
    val main = """
    package pp {
      class X
    }
    object %s extends App {
      println("ok")
    }
    """.format(mainName)

    createSource(EmptyPackage, mainName, main)
    val mainTypeName = mainName

    runTest(mainTypeName, main, never())
  }

  private def runTest(mainTypeName: String, mainContent: String, expectedCallsToErrorReportder: VerificationMode): Unit = {
    project.underlying.build(IncrementalProjectBuilder.FULL_BUILD, new NullProgressMonitor)

    val reporter = mock(classOf[MainClassVerifier.ErrorReporter])
    val verifier = new MainClassVerifier(reporter)

    verifier.execute(project, mainTypeName)

    verify(reporter, expectedCallsToErrorReportder).report(any())
  }

  private def createSource(pkgName: String, typeName: String, content: String): Unit = {
    val pkg = simulator.createPackage(pkgName)
    val fileName = typeName + ".scala"
    simulator.createCompilationUnit(pkg, fileName, content).asInstanceOf[ScalaCompilationUnit]
  }
}

object MainClassVerifierTest {
  private final val EmptyPackage = ""
}