package scala.tools.eclipse.launching

import scala.tools.eclipse.EclipseUserSimulator
import scala.tools.eclipse.ScalaProject
import scala.tools.eclipse.testsetup.SDTTestUtils._
import scala.tools.eclipse.testsetup.SDTTestUtils
import scala.tools.eclipse.testsetup.TestProjectSetup
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import scala.tools.eclipse.javaelements.ScalaSourceFile

/** This class checks the functionality behind Run As > JUnit Test, triggered when a user right clicks on a source
  * file in the package explorer and hovers on "Run As". If the source file contains any runnable JUnit Test class,
  * then a "JUnit Test" option is displayed.
  *
  * Mind that the tests in this class don't actually check the UI functionality, but only the underlying logic.
  * Furthermore, right clicking on a source or on a package (or a project) it's not the same.
  */
class JUnitTestClassesFinderTest {

  private final val TestProjectName = "runAsJunit"

  private val simulator = new EclipseUserSimulator
  private var projectSetup: TestProjectSetup = _

  @Before
  def createProject() {
    val scalaProject = simulator.createProjectInWorkspace(TestProjectName, withSourceRoot = true, withJUnitTestContainer = true)
    projectSetup = new TestProjectSetup(TestProjectName) {
      override lazy val project = scalaProject
    }
  }

  @After
  def deleteProject() {
    SDTTestUtils.deleteProjects(project)
  }

  def project: ScalaProject = projectSetup.project

  @Test
  def findSimpleTestClass() {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |class MyTest {
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set("MyTest")
  }

  @Test
  def findSimpleTestClass_afterTypecheckingSource() {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |class MyTest {
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }
    projectSetup.waitUntilTypechecked(cu)

    runnableJUnitTestClassesIn(cu) matches Set("MyTest")
  }

  /** In this test the JUnit Test class is found because the source has not been typechecked yet, i.e., we
    * cannot tell at this point that the JUnit Test class isn't runnable. For this, we need a typecheked
    * source, as the next test shows.
    */
  @Test
  def findTestClass_WhenThereAreParseErrors() {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |class MyTest {
        |  s //unknown identifier
        |  @Test
        |  def test1() {}
        | // unclosed class
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set("MyTest")
  }

  /** In this test the JUnit Test class is NOT found because the source has been typechecked. */
  @Test
  def findTestClass_WhenThereAreTypecheckingError() {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |class MyTest {
        |  s //unknown identifier
        |  @Test
        |  def test1() {}
        | // unclosed class
      """.stripMargin
    }
    projectSetup.waitUntilTypechecked(cu)

    runnableJUnitTestClassesIn(cu) matches Set.empty
  }

  @Test
  def cannotFindTestClass_ifJUnitTestAnnotationIsNotImported() {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |class MyTest {
        |  @Test  // Test annotation is not fully qualified, hence it cannot be found
        |  def test1() {}
        |}
      """.stripMargin
    }
    projectSetup.waitUntilTypechecked(cu)

    runnableJUnitTestClassesIn(cu) matches Set.empty
  }

  @Test
  def findMultipleTestClasses() {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |class MyTest1 {
        |  @Test
        |  def test1() {}
        |}
        |
        |class MyTest2 {
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set("MyTest1", "MyTest2")
  }

  @Test
  def dontLookInModulesForTests() {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |object MyTest {
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set.empty
  }

  @Test
  def dontLookInTraitForTests() {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |trait SuperTest {
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set.empty
  }

  @Test
  def dontLookInAbstractClassesForTests() {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |abstract class AbstractTest {
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set.empty
  }

  @Test
  def cannotFindTestClass_WhenDefinedInSuperTrait() {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |trait SuperTest {
        |  @Test
        |  def test1() {}
        |}
        |class MyTest extends SuperTrait
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set.empty
  }

  @Test
  def cannotRunAbstractJUnitTestClass() {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |abstract class MyTest {
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set.empty
  }

  @Ignore("Enable this when ticket #1001379 is fixed")
  @Test
  def findTestClass_WhenTestMethodIsDefinedInAbstractParent() {
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |import org.junit.Test
        |abstract class SuperTest {
        |  @Test
        |  def test1() {}
        |}
        |class MyTest extends SuperTest
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set("MyTest")
  }

  @Ignore("Enable this when ticket #1001379 is fixed")
  @Test
  def findTestClass2_WhenDefinedInAbstractParent_andSeparateCompilationUnit() {
    projectSetup.createSourceFile("test", "SuperTest.scala") {
      """
        |package test
        |import org.junit.Test
        |abstract class SuperTest {
        |  @Test
        |  def test1() {}
        |}
      """.stripMargin
    }
    val cu = projectSetup.createSourceFile("test", "MyTest.scala") {
      """
        |package test
        |class MyTest extends SuperTest
      """.stripMargin
    }

    runnableJUnitTestClassesIn(cu) matches Set("MyTest")
  }

  private def runnableJUnitTestClassesIn(source: ScalaSourceFile) = new {
    def matches(expectedClassNames: Set[String]): Unit = {
      val jUnitClasses = ScalaLaunchShortcut.getJunitTestClasses(source).toList.map(_.getElementName)
      Assert.assertEquals("test classes found.", expectedClassNames, jUnitClasses.toSet)
    }
  }
}