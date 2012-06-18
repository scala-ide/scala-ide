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

@RunWith(classOf[JUnit4])
class MainClassVerifierTest {
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

  @Test
  def reportErrorWhenMainContainsCompilationErrors() {
    val noPkg = ""
    val mainName = "MainWithCompilationErrors"
    val main = """
    object %s extends App {
      foo // <-- foo is an unkown identifier
    }
    """.format(mainName)

    createSource(noPkg, mainName, main)
    val mainTypeName = mainName

    runTest(mainTypeName, main, times(1)) // if there are compilation errors then no binaries are produced!
  }

  @Test
  def reportErrorWhenPackageDeclarationInMainTypeDoesntMatchPhysicalLocation() {
    val pkg = "foo"
    val mainName = "Main"
    val main = "object %s extends App".format(mainName) // note: no package declaration here!

    createSource(pkg, mainName, main) // source is created in foo/ (look at the value of `pkg`
    val mainTypeName = mainName       // this is correct fully-qualified name

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